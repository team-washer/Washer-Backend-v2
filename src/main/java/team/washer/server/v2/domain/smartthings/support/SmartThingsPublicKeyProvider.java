package team.washer.server.v2.domain.smartthings.support;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.cert.CertificateFactory;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;
import team.washer.server.v2.global.thirdparty.smartthings.config.SmartThingsWebhookEnvironment;

/**
 * SmartThings webhook 서명 검증용 공개키를 {@code keyUrl + keyId}에서 조회하고 캐시한다.
 *
 * <p>
 * keyId는 요청 헤더에서 오는 값이므로 경로 조각만 허용해 다른 host나 상위 경로 조회(SSRF)를 막는다. 공식 문서에 따라
 * key는 불변이고 교체 시 keyId가 바뀌므로 keyId 단위로 캐시한다.
 * </p>
 */
@Component
@Slf4j
public class SmartThingsPublicKeyProvider {

    private static final Pattern ALLOWED_KEY_ID = Pattern.compile("^(/[A-Za-z0-9_-]+)+$");
    private static final int MAX_CACHED_KEYS = 32;
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);

    private final SmartThingsWebhookEnvironment environment;
    private final HttpClient httpClient;
    private final Map<String, CachedKey> cache = new ConcurrentHashMap<>();

    public SmartThingsPublicKeyProvider(final SmartThingsWebhookEnvironment environment) {
        this.environment = environment;
        this.httpClient = HttpClient.newBuilder().connectTimeout(REQUEST_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NEVER).build();
    }

    /**
     * keyId가 공개키 조회에 허용되는 경로 형식인지 확인한다.
     *
     * @param keyId
     *            Signature 헤더의 keyId
     * @return 허용 여부
     */
    public boolean isAllowedKeyId(final String keyId) {
        return keyId != null && ALLOWED_KEY_ID.matcher(keyId).matches();
    }

    /**
     * keyId에 해당하는 공개키를 반환한다.
     *
     * @param keyId
     *            {@link #isAllowedKeyId(String)}를 통과한 keyId
     * @return RSA 공개키
     * @throws IllegalStateException
     *             조회나 파싱에 실패한 경우
     */
    public PublicKey getPublicKey(final String keyId) {
        if (!isAllowedKeyId(keyId)) {
            throw new IllegalArgumentException("허용되지 않은 SmartThings keyId입니다.");
        }
        final var now = Instant.now();
        final var cached = cache.get(keyId);
        if (cached != null && cached.expiresAt().isAfter(now)) {
            return cached.key();
        }

        final var key = parsePublicKey(fetch(keyId));
        if (cache.size() >= MAX_CACHED_KEYS) {
            cache.clear();
        }
        cache.put(keyId, new CachedKey(key, now.plus(Duration.ofMinutes(environment.keyCacheTtlMinutes()))));
        log.info("SmartThings webhook public key loaded keyId={}", keyId);
        return key;
    }

    private byte[] fetch(final String keyId) {
        final var request = HttpRequest.newBuilder(URI.create(environment.keyUrl() + keyId)).timeout(REQUEST_TIMEOUT)
                .GET().build();
        try {
            final var response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200) {
                throw new IllegalStateException("SmartThings 공개키 조회에 실패했습니다. status=" + response.statusCode());
            }
            return response.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("SmartThings 공개키 조회가 중단되었습니다.", e);
        } catch (IOException e) {
            throw new IllegalStateException("SmartThings 공개키 조회에 실패했습니다.", e);
        }
    }

    /**
     * 공식 문서의 X.509 인증서(PEM/DER)와 PEM 공개키(SubjectPublicKeyInfo)를 모두 받는다.
     */
    static PublicKey parsePublicKey(final byte[] body) {
        try {
            final var text = new String(body, StandardCharsets.US_ASCII);
            if (text.contains("BEGIN PUBLIC KEY")) {
                final var base64 = text.replaceAll("-----[A-Z ]+-----", "").replaceAll("\\s", "");
                return KeyFactory.getInstance("RSA")
                        .generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(base64)));
            }
            return CertificateFactory.getInstance("X.509").generateCertificate(new ByteArrayInputStream(body))
                    .getPublicKey();
        } catch (Exception e) {
            throw new IllegalStateException("SmartThings 공개키 형식을 해석할 수 없습니다.", e);
        }
    }

    private record CachedKey(PublicKey key, Instant expiresAt) {
    }
}
