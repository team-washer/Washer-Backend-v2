package team.washer.server.v2.domain.smartthings.support;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.Signature;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import team.washer.server.v2.domain.smartthings.enums.SmartThingsWebhookVerificationResult;
import team.washer.server.v2.global.thirdparty.smartthings.config.SmartThingsWebhookEnvironment;

/**
 * SmartThings webhook 요청의 HTTP Signature(draft-cavage, rsa-sha256)와 body
 * digest를 검증한다.
 *
 * <p>
 * 공식 문서는 {@code (request-target) digest date}를 서명 대상으로 쓰므로 세 항목이 모두 서명에 포함되어야
 * 통과시킨다. digest가 서명되지 않으면 body를 바꿔치기할 수 있고, date가 서명되지 않으면 재전송을 막을 수 없기 때문이다.
 * </p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SmartThingsWebhookSignatureVerifier {

    private static final String SIGNATURE_PREFIX = "Signature ";
    private static final String REQUEST_TARGET = "(request-target)";
    private static final List<String> REQUIRED_SIGNED_HEADERS = List.of(REQUEST_TARGET, "digest", "date");
    private static final Pattern SIGNATURE_PARAM = Pattern.compile("(\\w+)=\"([^\"]*)\"");

    private final SmartThingsWebhookEnvironment environment;
    private final SmartThingsPublicKeyProvider publicKeyProvider;

    /**
     * 요청 서명을 검증한다.
     *
     * @param request
     *            수신 요청
     * @param rawBody
     *            수신한 원본 body
     * @param receivedAt
     *            수신 시각
     * @return 검증 결과
     */
    public SmartThingsWebhookVerificationResult verify(final HttpServletRequest request,
            final byte[] rawBody,
            final Instant receivedAt) {
        final var authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith(SIGNATURE_PREFIX)) {
            return SmartThingsWebhookVerificationResult.MISSING_SIGNATURE;
        }
        final var params = parseSignatureParams(authorization.substring(SIGNATURE_PREFIX.length()));
        if (!"rsa-sha256".equalsIgnoreCase(params.getOrDefault("algorithm", "rsa-sha256"))) {
            return SmartThingsWebhookVerificationResult.UNSUPPORTED_ALGORITHM;
        }

        final var digestResult = verifyDigest(request.getHeader("Digest"), rawBody);
        if (!digestResult.isValid()) {
            return digestResult;
        }
        final var dateResult = verifyDate(request.getHeader("Date"), receivedAt);
        if (!dateResult.isValid()) {
            return dateResult;
        }

        final var signedHeaders = Arrays
                .asList(params.getOrDefault("headers", "date").toLowerCase(Locale.ROOT).trim().split("\\s+"));
        if (!signedHeaders.containsAll(REQUIRED_SIGNED_HEADERS)) {
            return SmartThingsWebhookVerificationResult.REQUIRED_HEADER_NOT_SIGNED;
        }
        final var signingString = buildSigningString(request, signedHeaders);
        if (signingString == null) {
            return SmartThingsWebhookVerificationResult.MISSING_SIGNED_HEADER;
        }

        final var keyId = params.get("keyId");
        if (!publicKeyProvider.isAllowedKeyId(keyId)) {
            return SmartThingsWebhookVerificationResult.KEY_ID_NOT_ALLOWED;
        }
        return verifySignature(keyId, signingString, params.get("signature"));
    }

    private Map<String, String> parseSignatureParams(final String value) {
        final var params = new HashMap<String, String>();
        final var matcher = SIGNATURE_PARAM.matcher(value);
        while (matcher.find()) {
            params.put(matcher.group(1), matcher.group(2));
        }
        return params;
    }

    /**
     * 공식 예시는 {@code SHA256=}, RFC 3230 표기는 {@code SHA-256=}이므로 두 표기를 모두 받는다. 여러
     * digest가 쉼표로 이어져 오면 SHA-256 항목만 비교한다.
     */
    private SmartThingsWebhookVerificationResult verifyDigest(final String digestHeader, final byte[] rawBody) {
        if (digestHeader == null || digestHeader.isBlank()) {
            return SmartThingsWebhookVerificationResult.MISSING_DIGEST;
        }
        for (final var entry : digestHeader.split(",")) {
            final var separator = entry.indexOf('=');
            if (separator < 0) {
                continue;
            }
            final var algorithm = entry.substring(0, separator).trim().replace("-", "");
            if (!"SHA256".equalsIgnoreCase(algorithm)) {
                continue;
            }
            try {
                final var expected = MessageDigest.getInstance("SHA-256").digest(rawBody);
                final var actual = Base64.getDecoder().decode(entry.substring(separator + 1).trim());
                return MessageDigest.isEqual(expected, actual)
                        ? SmartThingsWebhookVerificationResult.VALID
                        : SmartThingsWebhookVerificationResult.DIGEST_MISMATCH;
            } catch (IllegalArgumentException e) {
                return SmartThingsWebhookVerificationResult.DIGEST_MISMATCH;
            } catch (NoSuchAlgorithmException e) {
                throw new IllegalStateException("SHA-256 알고리즘을 사용할 수 없습니다.", e);
            }
        }
        return SmartThingsWebhookVerificationResult.UNSUPPORTED_DIGEST;
    }

    private SmartThingsWebhookVerificationResult verifyDate(final String dateHeader, final Instant receivedAt) {
        if (dateHeader == null || dateHeader.isBlank()) {
            return SmartThingsWebhookVerificationResult.MISSING_DATE;
        }
        try {
            // 공식 예시가 "... UTC" 표기를 쓰므로 RFC 1123의 GMT로 바꿔 파싱한다.
            final var sentAt = ZonedDateTime
                    .parse(dateHeader.trim().replaceFirst(" UTC$", " GMT"), DateTimeFormatter.RFC_1123_DATE_TIME)
                    .toInstant();
            final var skew = Duration.between(sentAt, receivedAt).abs();
            return skew.compareTo(Duration.ofSeconds(environment.maxDateSkewSeconds())) > 0
                    ? SmartThingsWebhookVerificationResult.DATE_SKEW
                    : SmartThingsWebhookVerificationResult.VALID;
        } catch (DateTimeParseException e) {
            return SmartThingsWebhookVerificationResult.INVALID_DATE;
        }
    }

    /**
     * 서명된 헤더 순서대로 signing string을 만든다. 서명 대상 헤더가 요청에 없으면 {@code null}을 반환한다.
     */
    private String buildSigningString(final HttpServletRequest request, final List<String> signedHeaders) {
        final var lines = new StringBuilder();
        for (final var name : signedHeaders) {
            if (!lines.isEmpty()) {
                lines.append('\n');
            }
            if (REQUEST_TARGET.equals(name)) {
                final var query = request.getQueryString();
                lines.append(REQUEST_TARGET).append(": ").append(request.getMethod().toLowerCase(Locale.ROOT))
                        .append(' ').append(request.getRequestURI()).append(query == null ? "" : "?" + query);
                continue;
            }
            final var value = request.getHeader(name);
            if (value == null) {
                return null;
            }
            lines.append(name).append(": ").append(value);
        }
        return lines.toString();
    }

    private SmartThingsWebhookVerificationResult verifySignature(final String keyId,
            final String signingString,
            final String signature) {
        if (signature == null || signature.isBlank()) {
            return SmartThingsWebhookVerificationResult.MISSING_SIGNATURE;
        }
        final PublicKey publicKey;
        try {
            publicKey = publicKeyProvider.getPublicKey(keyId);
        } catch (RuntimeException e) {
            log.warn("SmartThings webhook public key unavailable keyId={} error={}",
                    keyId,
                    e.getClass().getSimpleName());
            return SmartThingsWebhookVerificationResult.KEY_UNAVAILABLE;
        }
        try {
            final var verifier = Signature.getInstance("SHA256withRSA");
            verifier.initVerify(publicKey);
            verifier.update(signingString.getBytes(StandardCharsets.UTF_8));
            return verifier.verify(Base64.getDecoder().decode(signature))
                    ? SmartThingsWebhookVerificationResult.VALID
                    : SmartThingsWebhookVerificationResult.SIGNATURE_MISMATCH;
        } catch (IllegalArgumentException | GeneralSecurityException e) {
            return SmartThingsWebhookVerificationResult.SIGNATURE_MISMATCH;
        }
    }
}
