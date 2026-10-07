package team.washer.server.v2.domain.auth.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.Optional;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import team.themoment.datagsm.sdk.oauth.DataGsmOAuthClient;
import team.themoment.datagsm.sdk.oauth.exception.BadRequestException;
import team.themoment.datagsm.sdk.oauth.exception.DataGsmException;
import team.themoment.datagsm.sdk.oauth.exception.ForbiddenException;
import team.themoment.datagsm.sdk.oauth.exception.NotFoundException;
import team.themoment.datagsm.sdk.oauth.exception.RateLimitException;
import team.themoment.datagsm.sdk.oauth.exception.ServerErrorException;
import team.themoment.datagsm.sdk.oauth.exception.UnauthorizedException;
import team.themoment.datagsm.sdk.oauth.model.Student;
import team.themoment.datagsm.sdk.oauth.model.TokenResponse;
import team.themoment.datagsm.sdk.oauth.model.UserInfo;
import team.themoment.sdk.exception.ExpectedException;
import team.washer.server.v2.domain.auth.dto.request.TokenReqDto;
import team.washer.server.v2.domain.auth.dto.response.TokenResDto;
import team.washer.server.v2.domain.auth.repository.WithdrawnStudentRepository;
import team.washer.server.v2.domain.auth.service.impl.SignInServiceImpl;
import team.washer.server.v2.domain.auth.support.ExistingUserSignInSupport;
import team.washer.server.v2.domain.auth.support.TokenGenerationSupport;
import team.washer.server.v2.domain.auth.util.WithdrawnStudentRedisUtil;
import team.washer.server.v2.domain.user.entity.User;
import team.washer.server.v2.domain.user.support.UserRegistrationSupport;
import team.washer.server.v2.global.common.error.code.ErrorCode;
import team.washer.server.v2.global.common.error.exception.ErrorCodeException;

@ExtendWith(MockitoExtension.class)
@DisplayName("SignInServiceImpl 클래스의")
class SignInServiceImplTest {

    @InjectMocks
    private SignInServiceImpl signInService;

    @Mock
    private DataGsmOAuthClient oauthClient;

    @Mock
    private UserRegistrationSupport userRegistrationSupport;

    @Mock
    private ExistingUserSignInSupport existingUserSignInSupport;

    @Mock
    private TokenGenerationSupport tokenGenerationSupport;

    @Mock
    private WithdrawnStudentRedisUtil withdrawnStudentRedisUtil;

    @Mock
    private WithdrawnStudentRepository withdrawnStudentRepository;

    @Mock
    private TokenResponse tokenResponse;

    @Mock
    private UserInfo userInfoResponse;

    @Mock
    private Student student;

    private TokenReqDto createReqDto() {
        return createReqDto(null);
    }

    private TokenReqDto createReqDto(final String codeVerifier) {
        return new TokenReqDto("auth-code-123", "https://example.com/callback", codeVerifier);
    }

    private User createUser() {
        return User.builder().name("김철수").studentId("20210001").roomNumber("301").grade(3).floor(3).build();
    }

    @Nested
    @DisplayName("execute 메서드는")
    class Describe_execute {

        @Nested
        @DisplayName("유효한 인증 코드로 기존 사용자가 로그인할 때")
        class Context_with_existing_user {

            @Test
            @DisplayName("토큰을 반환해야 한다")
            void it_returns_tokens() {
                // Given
                var reqDto = createReqDto();
                var expectedTokens = new TokenResDto("access.token", 3600L, "refresh.token");

                given(oauthClient.exchangeCodeForToken("auth-code-123", "https://example.com/callback"))
                        .willReturn(tokenResponse);
                given(tokenResponse.getAccessToken()).willReturn("oauth-access-token");
                given(oauthClient.getUserInfo("oauth-access-token")).willReturn(userInfoResponse);
                given(userInfoResponse.getStudent()).willReturn(student);
                given(student.getStudentNumber()).willReturn(20210001);
                given(existingUserSignInSupport.generateIfExistingUser("20210001"))
                        .willReturn(Optional.of(expectedTokens));

                // When
                var result = signInService.execute(reqDto);

                // Then
                assertThat(result).isNotNull();
                assertThat(result.accessToken()).isEqualTo("access.token");
                assertThat(result.refreshToken()).isEqualTo("refresh.token");
                then(oauthClient).should(times(1)).exchangeCodeForToken("auth-code-123",
                        "https://example.com/callback");
                then(oauthClient).should(never())
                        .exchangeCodeForToken(anyString(), anyString(), ArgumentMatchers.<String>any());
                then(userRegistrationSupport).shouldHaveNoInteractions();
                then(withdrawnStudentRedisUtil).shouldHaveNoInteractions();
            }
        }

        @Nested
        @DisplayName("유효한 PKCE code verifier로 기존 사용자가 로그인할 때")
        class Context_with_pkce_code_verifier {

            @Test
            @DisplayName("원본 verifier로 3인자 토큰 교환만 수행해야 한다")
            void it_exchanges_token_with_pkce_overload() {
                // Given
                final var codeVerifier = "A".repeat(43);
                final var reqDto = createReqDto(codeVerifier);
                final var expectedTokens = new TokenResDto("access.token", 3600L, "refresh.token");
                given(oauthClient.exchangeCodeForToken("auth-code-123", "https://example.com/callback", codeVerifier))
                        .willReturn(tokenResponse);
                given(tokenResponse.getAccessToken()).willReturn("oauth-access-token");
                given(oauthClient.getUserInfo("oauth-access-token")).willReturn(userInfoResponse);
                given(userInfoResponse.getStudent()).willReturn(student);
                given(student.getStudentNumber()).willReturn(20210001);
                given(existingUserSignInSupport.generateIfExistingUser("20210001"))
                        .willReturn(Optional.of(expectedTokens));

                // When
                final var result = signInService.execute(reqDto);

                // Then
                assertThat(result).isEqualTo(expectedTokens);
                then(oauthClient).should(times(1))
                        .exchangeCodeForToken("auth-code-123", "https://example.com/callback", codeVerifier);
                then(oauthClient).should(never()).exchangeCodeForToken("auth-code-123", "https://example.com/callback");
            }

            @Test
            @DisplayName("PKCE 로그인 성공 로그에는 PKCE 여부만 포함되어야 한다")
            void it_logs_pkce_success_without_authentication_values() {
                // Given
                final var codeVerifier = "A".repeat(43);
                final var reqDto = createReqDto(codeVerifier);
                final var expectedTokens = new TokenResDto("access.token", 3600L, "refresh.token");
                final var logger = (Logger) LoggerFactory.getLogger(SignInServiceImpl.class);
                final var appender = new ListAppender<ILoggingEvent>();
                appender.start();
                logger.addAppender(appender);
                given(oauthClient.exchangeCodeForToken("auth-code-123", "https://example.com/callback", codeVerifier))
                        .willReturn(tokenResponse);
                given(tokenResponse.getAccessToken()).willReturn("oauth-access-token");
                given(oauthClient.getUserInfo("oauth-access-token")).willReturn(userInfoResponse);
                given(userInfoResponse.getStudent()).willReturn(student);
                given(student.getStudentNumber()).willReturn(20210001);
                given(existingUserSignInSupport.generateIfExistingUser("20210001"))
                        .willReturn(Optional.of(expectedTokens));

                try {
                    // When
                    final var result = signInService.execute(reqDto);

                    // Then
                    assertThat(result).isEqualTo(expectedTokens);
                    assertThat(appender.list).singleElement().satisfies(event -> {
                        assertThat(event.getFormattedMessage()).contains("pkce=true")
                                .doesNotContain("auth-code-123", codeVerifier, "oauth-access-token");
                    });
                } finally {
                    logger.detachAppender(appender);
                    appender.stop();
                }
            }

            @Test
            @DisplayName("빈 verifier는 2인자 레거시 토큰 교환으로 fallback하지 않아야 한다")
            void it_does_not_downgrade_non_null_verifier() {
                // Given
                final var reqDto = createReqDto("");

                // When & Then
                assertThatThrownBy(() -> signInService.execute(reqDto)).isInstanceOf(ExpectedException.class).satisfies(
                        e -> assertThat(((ExpectedException) e).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
                then(oauthClient).shouldHaveNoInteractions();
            }

            @Test
            @DisplayName("PKCE 토큰 교환 거부 로그에 verifier와 인증 코드가 없어야 한다")
            void it_logs_pkce_rejection_without_authentication_values() {
                // Given
                final var codeVerifier = "A".repeat(43);
                final var reqDto = createReqDto(codeVerifier);
                final var logger = (Logger) LoggerFactory.getLogger(SignInServiceImpl.class);
                final var appender = new ListAppender<ILoggingEvent>();
                appender.start();
                logger.addAppender(appender);
                given(oauthClient.exchangeCodeForToken("auth-code-123", "https://example.com/callback", codeVerifier))
                        .willThrow(new UnauthorizedException("invalid code verifier"));

                try {
                    // When
                    assertThatThrownBy(() -> signInService.execute(reqDto)).isInstanceOf(ExpectedException.class);

                    // Then
                    assertThat(appender.list).singleElement().satisfies(event -> {
                        assertThat(event.getFormattedMessage()).contains("operation=token_exchange", "pkce=true")
                                .doesNotContain("auth-code-123", codeVerifier, "invalid code verifier");
                    });
                } finally {
                    logger.detachAppender(appender);
                    appender.stop();
                }
            }
        }

        @Nested
        @DisplayName("DataGSM 인증 정보가 유효하지 않을 때")
        class Context_with_invalid_datagsm_authentication {

            @Test
            @DisplayName("토큰 교환의 잘못된 요청은 401 ExpectedException으로 변환해야 한다")
            void it_converts_bad_request_to_unauthorized() {
                // Given
                final var reqDto = createReqDto();
                given(oauthClient.exchangeCodeForToken("auth-code-123", "https://example.com/callback"))
                        .willThrow(new BadRequestException("invalid authorization code"));

                // When & Then
                assertThatThrownBy(() -> signInService.execute(reqDto)).isInstanceOf(ExpectedException.class)
                        .hasMessage("인증 정보가 올바르지 않습니다. 다시 로그인해 주세요.")
                        .satisfies(e -> assertThat(((ExpectedException) e).getStatusCode())
                                .isEqualTo(HttpStatus.UNAUTHORIZED));
            }

            @Test
            @DisplayName("토큰 교환 거부는 인증 값 없이 운영 로그에 남겨야 한다")
            void it_logs_token_exchange_rejection_without_authentication_values() {
                // Given
                final var reqDto = createReqDto();
                final var logger = (Logger) LoggerFactory.getLogger(SignInServiceImpl.class);
                final var appender = new ListAppender<ILoggingEvent>();
                appender.start();
                logger.addAppender(appender);
                given(oauthClient.exchangeCodeForToken("auth-code-123", "https://example.com/callback"))
                        .willThrow(new UnauthorizedException("invalid authorization code"));

                try {
                    // When
                    assertThatThrownBy(() -> signInService.execute(reqDto)).isInstanceOf(ExpectedException.class);

                    // Then
                    assertThat(appender.list).singleElement().satisfies(event -> {
                        assertThat(event.getFormattedMessage()).contains("operation=token_exchange", "pkce=false")
                                .doesNotContain("auth-code-123", "invalid authorization code");
                    });
                } finally {
                    logger.detachAppender(appender);
                    appender.stop();
                }
            }

            @Test
            @DisplayName("사용자 정보 조회의 인증 실패는 401 ExpectedException으로 변환해야 한다")
            void it_converts_unauthorized_to_unauthorized() {
                // Given
                final var reqDto = createReqDto();
                given(oauthClient.exchangeCodeForToken("auth-code-123", "https://example.com/callback"))
                        .willReturn(tokenResponse);
                given(tokenResponse.getAccessToken()).willReturn("oauth-access-token");
                given(oauthClient.getUserInfo("oauth-access-token"))
                        .willThrow(new UnauthorizedException("invalid access token"));

                // When & Then
                assertThatThrownBy(() -> signInService.execute(reqDto)).isInstanceOf(ExpectedException.class)
                        .hasMessage("인증 정보가 올바르지 않습니다. 다시 로그인해 주세요.")
                        .satisfies(e -> assertThat(((ExpectedException) e).getStatusCode())
                                .isEqualTo(HttpStatus.UNAUTHORIZED));
            }

            @Test
            @DisplayName("PKCE 사용자 정보 조회 거절 로그에는 PKCE 여부만 포함되어야 한다")
            void it_logs_pkce_user_info_rejection_without_authentication_values() {
                // Given
                final var codeVerifier = "A".repeat(43);
                final var reqDto = createReqDto(codeVerifier);
                final var logger = (Logger) LoggerFactory.getLogger(SignInServiceImpl.class);
                final var appender = new ListAppender<ILoggingEvent>();
                appender.start();
                logger.addAppender(appender);
                given(oauthClient.exchangeCodeForToken("auth-code-123", "https://example.com/callback", codeVerifier))
                        .willReturn(tokenResponse);
                given(tokenResponse.getAccessToken()).willReturn("oauth-access-token");
                given(oauthClient.getUserInfo("oauth-access-token"))
                        .willThrow(new UnauthorizedException("invalid access token"));

                try {
                    // When
                    assertThatThrownBy(() -> signInService.execute(reqDto)).isInstanceOf(ExpectedException.class);

                    // Then
                    assertThat(appender.list).singleElement().satisfies(event -> {
                        assertThat(event.getFormattedMessage()).contains("operation=user_info", "pkce=true")
                                .doesNotContain("auth-code-123",
                                        codeVerifier,
                                        "oauth-access-token",
                                        "invalid access token");
                    });
                } finally {
                    logger.detachAppender(appender);
                    appender.stop();
                }
            }

            @Test
            @DisplayName("403 응답 원문은 노출하지 않고 내부 오류로 변환해야 한다")
            void it_maps_forbidden_to_internal_server_error() {
                // Given
                final var reqDto = createReqDto();
                given(oauthClient.exchangeCodeForToken("auth-code-123", "https://example.com/callback"))
                        .willThrow(new ForbiddenException("forbidden"));

                // When & Then
                assertInternalServerError(() -> signInService.execute(reqDto));
            }

            @Test
            @DisplayName("404 응답 원문은 노출하지 않고 내부 오류로 변환해야 한다")
            void it_maps_not_found_to_internal_server_error() {
                // Given
                final var reqDto = createReqDto();
                given(oauthClient.exchangeCodeForToken("auth-code-123", "https://example.com/callback"))
                        .willReturn(tokenResponse);
                given(tokenResponse.getAccessToken()).willReturn("oauth-access-token");
                given(oauthClient.getUserInfo("oauth-access-token")).willThrow(new NotFoundException("not found"));

                // When & Then
                assertInternalServerError(() -> signInService.execute(reqDto));
            }
        }

        @Nested
        @DisplayName("DataGSM 일시 장애가 발생할 때")
        class Context_with_transient_datagsm_failure {

            @Test
            @DisplayName("토큰 교환의 408 응답은 SERVICE_UNAVAILABLE로 변환해야 한다")
            void it_maps_token_exchange_timeout_response_to_service_unavailable() {
                // Given
                final var reqDto = createReqDto();
                given(oauthClient.exchangeCodeForToken("auth-code-123", "https://example.com/callback"))
                        .willThrow(new DataGsmException("provider response", HttpStatus.REQUEST_TIMEOUT.value()));

                // When & Then
                assertServiceUnavailable(() -> signInService.execute(reqDto));
            }

            @Test
            @DisplayName("사용자 정보 조회의 429 응답은 SERVICE_UNAVAILABLE로 변환해야 한다")
            void it_maps_user_info_rate_limit_to_service_unavailable() {
                // Given
                final var reqDto = createReqDto();
                given(oauthClient.exchangeCodeForToken("auth-code-123", "https://example.com/callback"))
                        .willReturn(tokenResponse);
                given(tokenResponse.getAccessToken()).willReturn("oauth-access-token");
                given(oauthClient.getUserInfo("oauth-access-token")).willThrow(new RateLimitException("rate limit"));

                // When & Then
                assertServiceUnavailable(() -> signInService.execute(reqDto));
            }

            @Test
            @DisplayName("토큰 교환의 5xx 응답은 SERVICE_UNAVAILABLE로 변환해야 한다")
            void it_maps_token_exchange_server_error_to_service_unavailable() {
                // Given
                final var reqDto = createReqDto();
                given(oauthClient.exchangeCodeForToken("auth-code-123", "https://example.com/callback"))
                        .willThrow(new ServerErrorException("provider server error"));

                // When & Then
                assertServiceUnavailable(() -> signInService.execute(reqDto));
            }

            @Test
            @DisplayName("PKCE 토큰 교환의 일반 5xx 응답을 SERVICE_UNAVAILABLE로 변환해야 한다")
            void it_maps_pkce_token_exchange_unclassified_server_error_to_service_unavailable() {
                // Given
                final var codeVerifier = "A".repeat(43);
                final var reqDto = createReqDto(codeVerifier);
                given(oauthClient.exchangeCodeForToken("auth-code-123", "https://example.com/callback", codeVerifier))
                        .willThrow(new DataGsmException("provider server error", 521));

                // When & Then
                assertServiceUnavailable(() -> signInService.execute(reqDto));
                then(oauthClient).should(times(1))
                        .exchangeCodeForToken("auth-code-123", "https://example.com/callback", codeVerifier);
                then(oauthClient).should(never()).exchangeCodeForToken("auth-code-123", "https://example.com/callback");
            }

            @Test
            @DisplayName("사용자 정보 조회의 연결 실패는 SERVICE_UNAVAILABLE로 변환해야 한다")
            void it_maps_user_info_connection_failure_to_service_unavailable() {
                // Given
                final var reqDto = createReqDto();
                given(oauthClient.exchangeCodeForToken("auth-code-123", "https://example.com/callback"))
                        .willReturn(tokenResponse);
                given(tokenResponse.getAccessToken()).willReturn("oauth-access-token");
                given(oauthClient.getUserInfo("oauth-access-token"))
                        .willThrow(new DataGsmException("connection failed", new ConnectException("refused")));

                // When & Then
                assertServiceUnavailable(() -> signInService.execute(reqDto));
            }

            @Test
            @DisplayName("토큰 교환의 읽기 timeout은 SERVICE_UNAVAILABLE로 변환해야 한다")
            void it_maps_token_exchange_read_timeout_to_service_unavailable() {
                // Given
                final var reqDto = createReqDto();
                given(oauthClient.exchangeCodeForToken("auth-code-123", "https://example.com/callback"))
                        .willThrow(new DataGsmException("read failed", new SocketTimeoutException("read timed out")));

                // When & Then
                assertServiceUnavailable(() -> signInService.execute(reqDto));
            }

            @Test
            @DisplayName("상태 코드 없는 파싱 오류는 일시 장애로 변환하지 않아야 한다")
            void it_does_not_map_parse_failure_to_service_unavailable() {
                // Given
                final var reqDto = createReqDto();
                given(oauthClient.exchangeCodeForToken("auth-code-123", "https://example.com/callback")).willThrow(
                        new DataGsmException("provider response", new IllegalArgumentException("parse failed")));

                // When & Then
                assertThatThrownBy(() -> signInService.execute(reqDto)).isInstanceOf(ErrorCodeException.class)
                        .extracting(exception -> ((ErrorCodeException) exception).getErrorCode())
                        .isEqualTo(ErrorCode.INTERNAL_SERVER_ERROR);
            }

            @Test
            @DisplayName("일시 장애 로그와 응답에는 외부 오류 원문을 포함하지 않아야 한다")
            void it_does_not_expose_provider_error_details() {
                // Given
                final var reqDto = createReqDto();
                final var logger = (Logger) LoggerFactory.getLogger(SignInServiceImpl.class);
                final var appender = new ListAppender<ILoggingEvent>();
                appender.start();
                logger.addAppender(appender);
                given(oauthClient.exchangeCodeForToken("auth-code-123", "https://example.com/callback"))
                        .willThrow(new RateLimitException("provider-secret-response"));

                try {
                    // When & Then
                    assertServiceUnavailable(() -> signInService.execute(reqDto));
                    assertThat(appender.list).singleElement().satisfies(event -> {
                        assertThat(event.getFormattedMessage()).contains("operation=token_exchange", "status=429")
                                .doesNotContain("auth-code-123", "provider-secret-response");
                    });
                } finally {
                    logger.detachAppender(appender);
                    appender.stop();
                }
            }
        }

        @Nested
        @DisplayName("유효한 인증 코드로 신규 사용자가 로그인할 때")
        class Context_with_new_user {

            @Test
            @DisplayName("사용자를 등록하고 토큰을 반환해야 한다")
            void it_registers_user_and_returns_tokens() {
                // Given
                var reqDto = createReqDto();
                var newUser = createUser();
                var expectedTokens = new TokenResDto("new.access.token", 3600L, "new.refresh.token");

                given(oauthClient.exchangeCodeForToken("auth-code-123", "https://example.com/callback"))
                        .willReturn(tokenResponse);
                given(tokenResponse.getAccessToken()).willReturn("oauth-access-token");
                given(oauthClient.getUserInfo("oauth-access-token")).willReturn(userInfoResponse);
                given(userInfoResponse.getStudent()).willReturn(student);
                given(student.getStudentNumber()).willReturn(20210001);
                given(existingUserSignInSupport.generateIfExistingUser("20210001")).willReturn(Optional.empty());
                given(withdrawnStudentRedisUtil.isWithdrawnRecently("20210001")).willReturn(false);
                given(userRegistrationSupport.register(student)).willReturn(newUser);
                given(tokenGenerationSupport.generate(newUser.getId(), newUser.getRole())).willReturn(expectedTokens);

                // When
                var result = signInService.execute(reqDto);

                // Then
                assertThat(result).isNotNull();
                then(userRegistrationSupport).should(times(1)).register(student);
            }
        }

        @Nested
        @DisplayName("학생 정보가 없는 DataGSM 계정으로 로그인할 때")
        class Context_with_no_student_info {

            @Test
            @DisplayName("ExpectedException이 발생하고 BAD_REQUEST 상태를 반환해야 한다")
            void it_throws_expected_exception_with_bad_request() {
                // Given
                var reqDto = createReqDto();

                given(oauthClient.exchangeCodeForToken("auth-code-123", "https://example.com/callback"))
                        .willReturn(tokenResponse);
                given(tokenResponse.getAccessToken()).willReturn("oauth-access-token");
                given(oauthClient.getUserInfo("oauth-access-token")).willReturn(userInfoResponse);
                given(userInfoResponse.getStudent()).willReturn(null);

                // When & Then
                assertThatThrownBy(() -> signInService.execute(reqDto)).isInstanceOf(ExpectedException.class)
                        .hasMessage("학생정보가 없는 DataGSM 계정입니다.")
                        .satisfies(e -> assertThat(((ExpectedException) e).getStatusCode())
                                .isEqualTo(HttpStatus.BAD_REQUEST));

                then(existingUserSignInSupport).shouldHaveNoInteractions();
            }
        }

        @Nested
        @DisplayName("탈퇴 후 30일이 지나지 않은 사용자가 로그인할 때")
        class Context_with_recently_withdrawn_user {

            @Test
            @DisplayName("탈퇴 제한 코드와 FORBIDDEN 상태를 반환해야 한다")
            void it_throws_expected_exception_with_forbidden() {
                // Given
                var reqDto = createReqDto();

                given(oauthClient.exchangeCodeForToken("auth-code-123", "https://example.com/callback"))
                        .willReturn(tokenResponse);
                given(tokenResponse.getAccessToken()).willReturn("oauth-access-token");
                given(oauthClient.getUserInfo("oauth-access-token")).willReturn(userInfoResponse);
                given(userInfoResponse.getStudent()).willReturn(student);
                given(student.getStudentNumber()).willReturn(20210001);
                given(existingUserSignInSupport.generateIfExistingUser("20210001")).willReturn(Optional.empty());
                given(withdrawnStudentRedisUtil.isWithdrawnRecently("20210001")).willReturn(true);

                // When & Then
                assertThatThrownBy(() -> signInService.execute(reqDto)).isInstanceOf(ErrorCodeException.class)
                        .hasMessage("탈퇴 후 30일이 지나지 않아 재가입할 수 없습니다.")
                        .satisfies(e -> assertThat(((ErrorCodeException) e).getErrorCode())
                                .isEqualTo(ErrorCode.WITHDRAWN_REJOIN_RESTRICTED));

                then(existingUserSignInSupport).should().generateIfExistingUser("20210001");
            }

            @Test
            @DisplayName("DB 탈퇴 기록이 있으면 Redis 기록이 없어도 재가입을 차단한다")
            void it_blocks_when_database_withdrawal_record_exists() {
                // Given
                var reqDto = createReqDto();

                given(oauthClient.exchangeCodeForToken("auth-code-123", "https://example.com/callback"))
                        .willReturn(tokenResponse);
                given(tokenResponse.getAccessToken()).willReturn("oauth-access-token");
                given(oauthClient.getUserInfo("oauth-access-token")).willReturn(userInfoResponse);
                given(userInfoResponse.getStudent()).willReturn(student);
                given(student.getStudentNumber()).willReturn(20210001);
                given(existingUserSignInSupport.generateIfExistingUser("20210001")).willReturn(Optional.empty());
                given(withdrawnStudentRepository.existsByStudentIdAndExpiresAtAfter(eq("20210001"), any()))
                        .willReturn(true);

                // When & Then
                assertThatThrownBy(() -> signInService.execute(reqDto)).isInstanceOf(ErrorCodeException.class)
                        .satisfies(e -> assertThat(((ErrorCodeException) e).getErrorCode())
                                .isEqualTo(ErrorCode.WITHDRAWN_REJOIN_RESTRICTED));

                then(withdrawnStudentRedisUtil).shouldHaveNoInteractions();
                then(userRegistrationSupport).shouldHaveNoInteractions();
            }
        }

        @Nested
        @DisplayName("동시 가입으로 DataIntegrityViolationException이 발생했을 때")
        class Context_with_data_integrity_violation {

            @Test
            @DisplayName("재조회하여 해당 사용자의 토큰을 반환해야 한다")
            void it_retries_find_and_returns_tokens() {
                // Given
                var reqDto = createReqDto();
                var user = createUser();
                var expectedTokens = new TokenResDto("access.token", 3600L, "refresh.token");

                given(oauthClient.exchangeCodeForToken("auth-code-123", "https://example.com/callback"))
                        .willReturn(tokenResponse);
                given(tokenResponse.getAccessToken()).willReturn("oauth-access-token");
                given(oauthClient.getUserInfo("oauth-access-token")).willReturn(userInfoResponse);
                given(userInfoResponse.getStudent()).willReturn(student);
                given(student.getStudentNumber()).willReturn(20210001);
                given(existingUserSignInSupport.generateIfExistingUser("20210001")).willReturn(Optional.empty(),
                        Optional.of(expectedTokens));
                given(withdrawnStudentRedisUtil.isWithdrawnRecently("20210001")).willReturn(false);
                given(userRegistrationSupport.register(student)).willThrow(new DataIntegrityViolationException("중복"));

                // When
                var result = signInService.execute(reqDto);

                // Then
                assertThat(result).isNotNull();
                then(existingUserSignInSupport).should(times(2)).generateIfExistingUser("20210001");
            }
        }

        @Test
        @DisplayName("신규 사용자의 탈퇴 기록 조회가 실패하면 가입을 진행하지 않고 Redis 오류를 전파해야 한다")
        void it_propagates_withdrawn_record_lookup_failure() {
            final var reqDto = createReqDto();

            given(oauthClient.exchangeCodeForToken("auth-code-123", "https://example.com/callback"))
                    .willReturn(tokenResponse);
            given(tokenResponse.getAccessToken()).willReturn("oauth-access-token");
            given(oauthClient.getUserInfo("oauth-access-token")).willReturn(userInfoResponse);
            given(userInfoResponse.getStudent()).willReturn(student);
            given(student.getStudentNumber()).willReturn(20210001);
            given(existingUserSignInSupport.generateIfExistingUser("20210001")).willReturn(Optional.empty());
            given(withdrawnStudentRedisUtil.isWithdrawnRecently("20210001"))
                    .willThrow(new org.springframework.data.redis.RedisConnectionFailureException("Redis unavailable"));

            assertThatThrownBy(() -> signInService.execute(reqDto))
                    .isInstanceOf(org.springframework.data.redis.RedisConnectionFailureException.class);
            then(userRegistrationSupport).shouldHaveNoInteractions();
        }

        private void assertServiceUnavailable(final ThrowingCallable callable) {
            assertThatThrownBy(callable).isInstanceOf(ErrorCodeException.class).satisfies(exception -> {
                final var errorCodeException = (ErrorCodeException) exception;
                assertThat(errorCodeException.getErrorCode()).isEqualTo(ErrorCode.SERVICE_UNAVAILABLE);
                assertThat(errorCodeException.getUserMessage()).isEqualTo(ErrorCode.SERVICE_UNAVAILABLE.getMessage());
            });
        }

        private void assertInternalServerError(final ThrowingCallable callable) {
            assertThatThrownBy(callable).isInstanceOf(ErrorCodeException.class).satisfies(exception -> {
                final var errorCodeException = (ErrorCodeException) exception;
                assertThat(errorCodeException.getErrorCode()).isEqualTo(ErrorCode.INTERNAL_SERVER_ERROR);
            });
        }
    }
}
