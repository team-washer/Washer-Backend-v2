package team.washer.server.v2.domain.auth.dto.request;

import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willReturn;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.cors.CorsConfigurationSource;

import jakarta.annotation.Resource;
import team.washer.server.v2.domain.auth.controller.AuthController;
import team.washer.server.v2.domain.auth.dto.response.TokenResDto;
import team.washer.server.v2.domain.auth.service.CheckTokenStatusService;
import team.washer.server.v2.domain.auth.service.RefreshTokenService;
import team.washer.server.v2.domain.auth.service.SignInService;
import team.washer.server.v2.global.common.error.handler.GlobalExceptionHandler;
import team.washer.server.v2.global.security.config.DomainAuthorizationConfig;
import team.washer.server.v2.global.security.config.SecurityConfig;
import team.washer.server.v2.global.security.jwt.provider.JwtTokenProvider;

@WebMvcTest(controllers = AuthController.class)
@ImportAutoConfiguration({SecurityAutoConfiguration.class, ServletWebSecurityAutoConfiguration.class})
@Import({SecurityConfig.class, DomainAuthorizationConfig.class, GlobalExceptionHandler.class,
        TokenReqDtoTest.TestBeansConfiguration.class})
@DisplayName("TokenReqDto의 HTTP 요청 바인딩은")
class TokenReqDtoTest {

    private static final String LOGIN_PATH = "/api/v2/auth/login";
    private static final String VALID_CODE_VERIFIER = "a".repeat(43);

    @Resource
    private MockMvc mockMvc;

    @MockitoBean
    private SignInService signInService;

    @MockitoBean
    private RefreshTokenService refreshTokenService;

    @MockitoBean
    private CheckTokenStatusService checkTokenStatusService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @Nested
    @DisplayName("선택적 codeVerifier가 없거나 유효할 때")
    class Context_with_valid_code_verifier {

        @Test
        @DisplayName("기존 로그인 JSON은 null codeVerifier로 서비스에 전달해야 한다")
        void it_binds_legacy_login_json() throws Exception {
            // Given
            willReturn(new TokenResDto("access.token", 3600L, "refresh.token")).given(signInService)
                    .execute(new TokenReqDto("auth-code-123", "https://example.com/callback", null));

            // When & Then
            mockMvc.perform(post(LOGIN_PATH).contentType(MediaType.APPLICATION_JSON).content("""
                    {"authCode":"auth-code-123","redirectUri":"https://example.com/callback"}
                    """)).andExpect(status().isOk());
            then(signInService).should()
                    .execute(new TokenReqDto("auth-code-123", "https://example.com/callback", null));
        }

        @Test
        @DisplayName("PKCE 로그인 JSON은 codeVerifier를 변경하지 않고 서비스에 전달해야 한다")
        void it_binds_pkce_code_verifier_without_modification() throws Exception {
            // Given
            final var request = new TokenReqDto("auth-code-123", "https://example.com/callback", VALID_CODE_VERIFIER);
            willReturn(new TokenResDto("access.token", 3600L, "refresh.token")).given(signInService).execute(request);

            // When & Then
            mockMvc.perform(post(LOGIN_PATH).contentType(MediaType.APPLICATION_JSON).content("""
                    {"authCode":"auth-code-123","redirectUri":"https://example.com/callback","codeVerifier":"%s"}
                    """.formatted(VALID_CODE_VERIFIER))).andExpect(status().isOk());
            then(signInService).should().execute(request);
        }
    }

    @Nested
    @DisplayName("codeVerifier 형식이 유효하지 않을 때")
    class Context_with_invalid_code_verifier {

        @ParameterizedTest
        @ValueSource(strings = {"", "too-short"})
        @DisplayName("형식 오류를 VALIDATION_FAILED와 fieldErrors로 응답해야 한다")
        void it_rejects_invalid_code_verifier(final String codeVerifier) throws Exception {
            mockMvc.perform(post(LOGIN_PATH).contentType(MediaType.APPLICATION_JSON).content("""
                    {"authCode":"auth-code-123","redirectUri":"https://example.com/callback","codeVerifier":"%s"}
                    """.formatted(codeVerifier))).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.data.errorCode").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.data.fieldErrors[0].field").value("codeVerifier"))
                    .andExpect(jsonPath("$.data.fieldErrors[0].message").value("code verifier 형식이 올바르지 않습니다"));
            then(signInService).shouldHaveNoInteractions();
        }
    }

    @TestConfiguration
    static class TestBeansConfiguration {

        @Bean(name = "configure")
        CorsConfigurationSource corsConfigurationSource() {
            return request -> null;
        }
    }
}
