package team.washer.server.v2.domain.smartthings.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.util.Base64;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import team.washer.server.v2.global.thirdparty.smartthings.config.SmartThingsWebhookEnvironment;

@DisplayName("SmartThingsPublicKeyProvider 클래스의")
class SmartThingsPublicKeyProviderTest {

    private final SmartThingsPublicKeyProvider provider = new SmartThingsPublicKeyProvider(
            new SmartThingsWebhookEnvironment(true, "app-1", null, 0, 0, 0, 0));

    @Nested
    @DisplayName("isAllowedKeyId 메서드는")
    class Describe_is_allowed_key_id {

        @Test
        @DisplayName("SmartThings 형식의 경로 keyId를 허용한다")
        void it_allows_path_key_id() {
            assertThat(provider.isAllowedKeyId("/pl/apnortheast2/1b-75-d2-ab")).isTrue();
        }

        @ParameterizedTest
        @ValueSource(strings = {"https://evil.example.com/key", "/pl/../secret", "//evil.example.com/key",
                "/pl/key?x=1", "pl/key", "/pl/key@evil", ""})
        @DisplayName("다른 host나 상위 경로를 가리킬 수 있는 keyId는 거부한다")
        void it_rejects_unsafe_key_id(final String keyId) {
            assertThat(provider.isAllowedKeyId(keyId)).isFalse();
        }
    }

    @Nested
    @DisplayName("parsePublicKey 메서드는")
    class Describe_parse_public_key {

        @Test
        @DisplayName("PEM 공개키를 RSA 공개키로 해석한다")
        void it_parses_pem_public_key() throws Exception {
            // Given
            final var generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            final var publicKey = generator.generateKeyPair().getPublic();
            final var pem = "-----BEGIN PUBLIC KEY-----\n"
                    + Base64.getMimeEncoder().encodeToString(publicKey.getEncoded()) + "\n-----END PUBLIC KEY-----\n";

            // When
            final var parsed = SmartThingsPublicKeyProvider.parsePublicKey(pem.getBytes(StandardCharsets.US_ASCII));

            // Then
            assertThat(parsed).isEqualTo(publicKey);
        }

        @Test
        @DisplayName("해석할 수 없는 형식이면 예외를 발생시킨다")
        void it_throws_when_format_is_invalid() {
            assertThatThrownBy(
                    () -> SmartThingsPublicKeyProvider.parsePublicKey("not a key".getBytes(StandardCharsets.US_ASCII)))
                    .isInstanceOf(IllegalStateException.class);
        }
    }
}
