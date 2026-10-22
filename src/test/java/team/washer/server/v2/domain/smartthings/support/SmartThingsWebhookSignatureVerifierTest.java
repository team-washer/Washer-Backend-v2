package team.washer.server.v2.domain.smartthings.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.Signature;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Base64;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import team.washer.server.v2.domain.smartthings.enums.SmartThingsWebhookVerificationResult;
import team.washer.server.v2.global.thirdparty.smartthings.config.SmartThingsWebhookEnvironment;

@DisplayName("SmartThingsWebhookSignatureVerifier 클래스의")
class SmartThingsWebhookSignatureVerifierTest {

    private static final String PATH = "/api/v2/events/smartthings";
    private static final String KEY_ID = "/pl/apnortheast2/test-key";
    private static final byte[] BODY = "{\"messageType\":\"EVENT\"}".getBytes(StandardCharsets.UTF_8);
    private static final Instant NOW = Instant.parse("2026-10-22T03:00:00Z");

    private static KeyPair keyPair;
    private static KeyPair otherKeyPair;

    private SmartThingsPublicKeyProvider publicKeyProvider;
    private SmartThingsWebhookSignatureVerifier verifier;

    @BeforeAll
    static void generateKeys() throws Exception {
        final var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        keyPair = generator.generateKeyPair();
        otherKeyPair = generator.generateKeyPair();
    }

    @BeforeEach
    void setUp() {
        publicKeyProvider = mock(SmartThingsPublicKeyProvider.class);
        given(publicKeyProvider.isAllowedKeyId(KEY_ID)).willReturn(true);
        given(publicKeyProvider.getPublicKey(KEY_ID)).willReturn(keyPair.getPublic());
        verifier = new SmartThingsWebhookSignatureVerifier(
                new SmartThingsWebhookEnvironment(true, "app-1", null, 0, 0, 0, 0),
                publicKeyProvider);
    }

    private static String digest(final byte[] body) throws Exception {
        return "SHA-256=" + Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(body));
    }

    private static String httpDate(final Instant instant) {
        return DateTimeFormatter.RFC_1123_DATE_TIME.format(instant.atOffset(ZoneOffset.UTC));
    }

    private static String sign(final PrivateKey privateKey, final String signingString) throws Exception {
        final var signer = Signature.getInstance("SHA256withRSA");
        signer.initSign(privateKey);
        signer.update(signingString.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(signer.sign());
    }

    /** SmartThings와 같은 방식으로 서명한 요청을 만든다. */
    private static MockHttpServletRequest signedRequest(final byte[] signedBody,
            final Instant sentAt,
            final String signedHeaders,
            final PrivateKey privateKey) throws Exception {
        final var request = new MockHttpServletRequest("POST", PATH);
        final var digest = digest(signedBody);
        final var date = httpDate(sentAt);
        request.addHeader("Digest", digest);
        request.addHeader("Date", date);

        final var signingString = new StringBuilder();
        for (final var name : signedHeaders.split(" ")) {
            if (!signingString.isEmpty()) {
                signingString.append('\n');
            }
            switch (name) {
                case "(request-target)" -> signingString.append("(request-target): post ").append(PATH);
                case "digest" -> signingString.append("digest: ").append(digest);
                case "date" -> signingString.append("date: ").append(date);
                default -> throw new IllegalArgumentException(name);
            }
        }
        request.addHeader("Authorization",
                "Signature keyId=\"" + KEY_ID + "\",signature=\"" + sign(privateKey, signingString.toString())
                        + "\",headers=\"" + signedHeaders + "\",algorithm=\"rsa-sha256\"");
        return request;
    }

    @Nested
    @DisplayName("verify 메서드는")
    class Describe_verify {

        @Test
        @DisplayName("SmartThings 공개키로 서명된 요청이면 VALID를 반환한다")
        void it_returns_valid_for_signed_request() throws Exception {
            // Given
            final var request = signedRequest(BODY, NOW, "(request-target) digest date", keyPair.getPrivate());

            // When
            final var result = verifier.verify(request, BODY, NOW);

            // Then
            assertThat(result).isEqualTo(SmartThingsWebhookVerificationResult.VALID);
        }

        @Test
        @DisplayName("본문이 서명 이후 바뀌면 DIGEST_MISMATCH를 반환한다")
        void it_rejects_tampered_body() throws Exception {
            // Given
            final var request = signedRequest(BODY, NOW, "(request-target) digest date", keyPair.getPrivate());
            final var tampered = "{\"messageType\":\"CONFIRMATION\"}".getBytes(StandardCharsets.UTF_8);

            // When
            final var result = verifier.verify(request, tampered, NOW);

            // Then
            assertThat(result).isEqualTo(SmartThingsWebhookVerificationResult.DIGEST_MISMATCH);
        }

        @Test
        @DisplayName("다른 키로 서명되면 SIGNATURE_MISMATCH를 반환한다")
        void it_rejects_signature_from_other_key() throws Exception {
            // Given
            final var request = signedRequest(BODY, NOW, "(request-target) digest date", otherKeyPair.getPrivate());

            // When
            final var result = verifier.verify(request, BODY, NOW);

            // Then
            assertThat(result).isEqualTo(SmartThingsWebhookVerificationResult.SIGNATURE_MISMATCH);
        }

        @Test
        @DisplayName("digest가 서명 대상에 없으면 REQUIRED_HEADER_NOT_SIGNED를 반환한다")
        void it_rejects_when_digest_is_not_signed() throws Exception {
            // Given
            final var request = signedRequest(BODY, NOW, "(request-target) date", keyPair.getPrivate());

            // When
            final var result = verifier.verify(request, BODY, NOW);

            // Then
            assertThat(result).isEqualTo(SmartThingsWebhookVerificationResult.REQUIRED_HEADER_NOT_SIGNED);
        }

        @Test
        @DisplayName("Date가 허용 오차를 넘으면 DATE_SKEW를 반환한다")
        void it_rejects_stale_request() throws Exception {
            // Given
            final var request = signedRequest(BODY,
                    NOW.minusSeconds(301),
                    "(request-target) digest date",
                    keyPair.getPrivate());

            // When
            final var result = verifier.verify(request, BODY, NOW);

            // Then
            assertThat(result).isEqualTo(SmartThingsWebhookVerificationResult.DATE_SKEW);
        }

        @Test
        @DisplayName("Signature 헤더가 없으면 MISSING_SIGNATURE를 반환한다")
        void it_rejects_request_without_signature() {
            // Given
            final var request = new MockHttpServletRequest("POST", PATH);
            request.addHeader("Authorization", "Bearer token");

            // When
            final var result = verifier.verify(request, BODY, NOW);

            // Then
            assertThat(result).isEqualTo(SmartThingsWebhookVerificationResult.MISSING_SIGNATURE);
        }

        @Test
        @DisplayName("허용되지 않은 keyId면 공개키를 조회하지 않고 KEY_ID_NOT_ALLOWED를 반환한다")
        void it_rejects_disallowed_key_id() throws Exception {
            // Given
            final var request = signedRequest(BODY, NOW, "(request-target) digest date", keyPair.getPrivate());
            given(publicKeyProvider.isAllowedKeyId(KEY_ID)).willReturn(false);

            // When
            final var result = verifier.verify(request, BODY, NOW);

            // Then
            assertThat(result).isEqualTo(SmartThingsWebhookVerificationResult.KEY_ID_NOT_ALLOWED);
        }

        @Test
        @DisplayName("공개키를 조회하지 못하면 KEY_UNAVAILABLE을 반환한다")
        void it_returns_key_unavailable_when_key_fetch_fails() throws Exception {
            // Given
            final var request = signedRequest(BODY, NOW, "(request-target) digest date", keyPair.getPrivate());
            given(publicKeyProvider.getPublicKey(KEY_ID)).willThrow(new IllegalStateException("조회 실패"));

            // When
            final var result = verifier.verify(request, BODY, NOW);

            // Then
            assertThat(result).isEqualTo(SmartThingsWebhookVerificationResult.KEY_UNAVAILABLE);
        }
    }
}
