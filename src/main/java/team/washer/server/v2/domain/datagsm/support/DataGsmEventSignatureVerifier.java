package team.washer.server.v2.domain.datagsm.support;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import team.washer.server.v2.global.thirdparty.datagsm.config.DataGsmEventEnvironment;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataGsmEventSignatureVerifier {

    private static final String SIGNATURE_PREFIX = "sha256=";
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final DataGsmEventEnvironment environment;

    public boolean verify(String signatureHeader, byte[] rawBody) {
        if (signatureHeader == null || !signatureHeader.startsWith(SIGNATURE_PREFIX)) {
            return false;
        }
        if (environment.secret() == null || environment.secret().isBlank()) {
            log.error("DataGSM event secret is missing");
            return false;
        }

        final var received = signatureHeader.substring(SIGNATURE_PREFIX.length());
        final var expected = calculateSignature(rawBody);
        return MessageDigest.isEqual(received.getBytes(StandardCharsets.UTF_8),
                expected.getBytes(StandardCharsets.UTF_8));
    }

    private String calculateSignature(byte[] rawBody) {
        try {
            final var mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(environment.secret().getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return HexFormat.of().formatHex(mac.doFinal(rawBody));
        } catch (Exception e) {
            throw new IllegalStateException("DataGSM 이벤트 서명 계산에 실패했습니다.", e);
        }
    }
}
