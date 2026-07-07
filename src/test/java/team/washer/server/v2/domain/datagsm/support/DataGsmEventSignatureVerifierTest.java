package team.washer.server.v2.domain.datagsm.support;

import static org.assertj.core.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Locale;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import team.washer.server.v2.global.thirdparty.datagsm.config.DataGsmEventEnvironment;

@DisplayName("DataGsmEventSignatureVerifier 클래스는")
class DataGsmEventSignatureVerifierTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    private final DataGsmEventSignatureVerifier verifier = new DataGsmEventSignatureVerifier(
            new DataGsmEventEnvironment(SECRET, 30));

    @Nested
    @DisplayName("verify 메서드는")
    class Describe_verify {

        @Test
        @DisplayName("올바른 HMAC-SHA256 서명이면 true를 반환한다")
        void it_returns_true_for_valid_signature() throws Exception {
            // Given
            final byte[] rawBody = "{\"id\":\"evt_1\"}".getBytes(StandardCharsets.UTF_8);
            final String signature = "sha256=" + calculateSignature(rawBody);

            // When
            final boolean result = verifier.verify(signature, rawBody);

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("서명 접두사와 Hex 값의 대소문자가 달라도 true를 반환한다")
        void it_returns_true_for_case_insensitive_signature() throws Exception {
            // Given
            final byte[] rawBody = "{\"id\":\"evt_1\"}".getBytes(StandardCharsets.UTF_8);
            final String signature = "  SHA256=" + calculateSignature(rawBody).toUpperCase(Locale.ROOT) + "  ";

            // When
            final boolean result = verifier.verify(signature, rawBody);

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("서명이 다르면 false를 반환한다")
        void it_returns_false_for_invalid_signature() {
            // Given
            final byte[] rawBody = "{\"id\":\"evt_1\"}".getBytes(StandardCharsets.UTF_8);

            // When
            final boolean result = verifier.verify("sha256=invalid", rawBody);

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("서명 헤더가 없으면 false를 반환한다")
        void it_returns_false_when_signature_header_is_missing() {
            // Given
            final byte[] rawBody = "{\"id\":\"evt_1\"}".getBytes(StandardCharsets.UTF_8);

            // When
            final boolean result = verifier.verify(null, rawBody);

            // Then
            assertThat(result).isFalse();
        }
    }

    private String calculateSignature(byte[] rawBody) throws Exception {
        final var mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(rawBody));
    }
}
