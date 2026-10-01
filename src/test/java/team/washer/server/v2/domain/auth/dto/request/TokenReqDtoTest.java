package team.washer.server.v2.domain.auth.dto.request;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.validation.Validation;

@DisplayName("TokenReqDto 클래스의")
class TokenReqDtoTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("기존 로그인 JSON은 codeVerifier 없이 역직렬화와 검증에 성공해야 한다")
    void it_accepts_legacy_login_json_without_code_verifier() throws Exception {
        final var json = """
                {
                  "authCode": "auth-code-123",
                  "redirectUri": "https://example.com/callback"
                }
                """;

        final var request = objectMapper.readValue(json, TokenReqDto.class);

        assertThat(request.authCode()).isEqualTo("auth-code-123");
        assertThat(request.redirectUri()).isEqualTo("https://example.com/callback");
        assertThat(request.codeVerifier()).isNull();
        try (final var validatorFactory = Validation.buildDefaultValidatorFactory()) {
            assertThat(validatorFactory.getValidator().validate(request)).isEmpty();
        }
    }

    @Test
    @DisplayName("PKCE 로그인 JSON은 codeVerifier를 변경하지 않고 역직렬화해야 한다")
    void it_binds_pkce_code_verifier_without_modification() throws Exception {
        final var json = """
                {
                  "authCode": "auth-code-123",
                  "redirectUri": "https://example.com/callback",
                  "codeVerifier": "pkce.verifier-123_ABC~value"
                }
                """;

        final var request = objectMapper.readValue(json, TokenReqDto.class);

        assertThat(request.codeVerifier()).isEqualTo("pkce.verifier-123_ABC~value");
        try (final var validatorFactory = Validation.buildDefaultValidatorFactory()) {
            assertThat(validatorFactory.getValidator().validate(request)).isEmpty();
        }
    }
}
