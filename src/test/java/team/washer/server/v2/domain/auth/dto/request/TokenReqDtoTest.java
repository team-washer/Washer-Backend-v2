package team.washer.server.v2.domain.auth.dto.request;

import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willReturn;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
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
import team.washer.server.v2.global.common.trace.TraceIdFilter;
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
    private static final String VALID_CODE_VERIFIER = "A".repeat(43);
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
    @DisplayName("로그인 요청 바인딩은")
    class Describe_login_request_binding {

        @Nested
        @DisplayName("codeVerifier가 없거나 null일 때")
        class Context_without_code_verifier {

            @Test
            @DisplayName("기존 로그인 JSON을 null verifier로 서비스에 전달해야 한다")
            void it_binds_legacy_login_json() throws Exception {
                // Given
                final var request = new TokenReqDto("auth-code-123", "https://example.com/callback", null);
                willReturn(new TokenResDto("access.token", 3600L, "refresh.token")).given(signInService)
                        .execute(request);

                // When & Then
                mockMvc.perform(post(LOGIN_PATH).contentType(MediaType.APPLICATION_JSON).content("""
                        {"authCode":"auth-code-123","redirectUri":"https://example.com/callback"}
                        """)).andExpect(status().isOk()).andExpect(jsonPath("$.accessToken").value("access.token"));
                then(signInService).should().execute(request);
            }

            @Test
            @DisplayName("명시적 null verifier도 기존 경로로 바인딩해야 한다")
            void it_binds_null_code_verifier() throws Exception {
                // Given
                final var request = new TokenReqDto("auth-code-123", "https://example.com/callback", null);
                willReturn(new TokenResDto("access.token", 3600L, "refresh.token")).given(signInService)
                        .execute(request);

                // When & Then
                mockMvc.perform(post(LOGIN_PATH).contentType(MediaType.APPLICATION_JSON).content("""
                        {"authCode":"auth-code-123","redirectUri":"https://example.com/callback","codeVerifier":null}
                        """)).andExpect(status().isOk());
                then(signInService).should().execute(request);
            }
        }

        @Nested
        @DisplayName("유효한 codeVerifier가 있을 때")
        class Context_with_valid_code_verifier {

            @ParameterizedTest
            @MethodSource("validCodeVerifiers")
            @DisplayName("원본 verifier를 서비스에 전달해야 한다")
            void it_binds_pkce_login_json(final String codeVerifier) throws Exception {
                // Given
                final var request = new TokenReqDto("auth-code-123", "https://example.com/callback", codeVerifier);
                willReturn(new TokenResDto("access.token", 3600L, "refresh.token")).given(signInService)
                        .execute(request);

                // When & Then
                mockMvc.perform(post(LOGIN_PATH).contentType(MediaType.APPLICATION_JSON).content("""
                        {"authCode":"auth-code-123","redirectUri":"https://example.com/callback","codeVerifier":"%s"}
                        """.formatted(codeVerifier))).andExpect(status().isOk());
                then(signInService).should().execute(request);
            }

            private static Stream<Arguments> validCodeVerifiers() {
                return Stream.of(Arguments.of(VALID_CODE_VERIFIER),
                        Arguments.of("A".repeat(128)),
                        Arguments.of("A".repeat(39) + "-._~"));
            }
        }

        @Nested
        @DisplayName("codeVerifier가 유효하지 않을 때")
        class Context_with_invalid_code_verifier {

            @ParameterizedTest
            @MethodSource("invalidCodeVerifiers")
            @DisplayName("validation error와 trace ID를 응답하고 서비스는 호출하지 않아야 한다")
            void it_rejects_invalid_code_verifier(final String codeVerifier) throws Exception {
                // When & Then
                mockMvc.perform(post(LOGIN_PATH).contentType(MediaType.APPLICATION_JSON).content("""
                        {"authCode":"auth-code-123","redirectUri":"https://example.com/callback","codeVerifier":"%s"}
                        """.formatted(codeVerifier))).andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.data.errorCode").value("VALIDATION_FAILED"))
                        .andExpect(jsonPath("$.data.fieldErrors[0].field").value("codeVerifier"))
                        .andExpect(jsonPath("$.data.fieldErrors[0].message").value("code verifier 형식이 올바르지 않습니다"))
                        .andExpect(header().exists(TraceIdFilter.TRACE_ID_HEADER));
                then(signInService).shouldHaveNoInteractions();
            }

            private static Stream<Arguments> invalidCodeVerifiers() {
                return Stream.of(Arguments.of(""),
                        Arguments.of(" ".repeat(43)),
                        Arguments.of("A".repeat(42)),
                        Arguments.of("A".repeat(129)),
                        Arguments.of("A".repeat(42) + "+"));
            }
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
