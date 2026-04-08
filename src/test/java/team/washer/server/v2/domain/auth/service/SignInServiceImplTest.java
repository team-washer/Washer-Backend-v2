package team.washer.server.v2.domain.auth.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;

import team.themoment.datagsm.sdk.oauth.DataGsmOAuthClient;
import team.themoment.datagsm.sdk.oauth.model.Student;
import team.themoment.datagsm.sdk.oauth.model.TokenResponse;
import team.themoment.datagsm.sdk.oauth.model.UserInfo;
import team.themoment.sdk.exception.ExpectedException;
import team.washer.server.v2.domain.auth.dto.request.TokenReqDto;
import team.washer.server.v2.domain.auth.dto.response.TokenResDto;
import team.washer.server.v2.domain.auth.service.impl.SignInServiceImpl;
import team.washer.server.v2.domain.auth.support.TokenGenerationSupport;
import team.washer.server.v2.domain.auth.util.WithdrawnStudentRedisUtil;
import team.washer.server.v2.domain.user.entity.User;
import team.washer.server.v2.domain.user.repository.UserRepository;
import team.washer.server.v2.domain.user.support.UserRegistrationSupport;

@ExtendWith(MockitoExtension.class)
@DisplayName("SignInServiceImpl 클래스의")
class SignInServiceImplTest {

    @InjectMocks
    private SignInServiceImpl signInService;

    @Mock
    private DataGsmOAuthClient oauthClient;

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserRegistrationSupport userRegistrationSupport;

    @Mock
    private TokenGenerationSupport tokenGenerationSupport;

    @Mock
    private WithdrawnStudentRedisUtil withdrawnStudentRedisUtil;

    @Mock
    private TokenResponse tokenResponse;

    @Mock
    private UserInfo userInfoResponse;

    @Mock
    private Student student;

    private TokenReqDto createReqDto() {
        return new TokenReqDto("auth-code-123", "https://example.com/callback");
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
                var user = createUser();
                var expectedTokens = new TokenResDto("access.token", 3600L, "refresh.token");

                given(oauthClient.exchangeCodeForToken("auth-code-123", "https://example.com/callback"))
                        .willReturn(tokenResponse);
                given(tokenResponse.getAccessToken()).willReturn("oauth-access-token");
                given(oauthClient.getUserInfo("oauth-access-token")).willReturn(userInfoResponse);
                given(userInfoResponse.getStudent()).willReturn(student);
                given(student.getStudentNumber()).willReturn(20210001);
                given(withdrawnStudentRedisUtil.isWithdrawnRecently("20210001")).willReturn(false);
                given(userRepository.findByStudentId("20210001")).willReturn(Optional.of(user));
                given(tokenGenerationSupport.generate(user.getId(), user.getRole())).willReturn(expectedTokens);

                // When
                var result = signInService.execute(reqDto);

                // Then
                assertThat(result).isNotNull();
                assertThat(result.accessToken()).isEqualTo("access.token");
                assertThat(result.refreshToken()).isEqualTo("refresh.token");
                then(userRegistrationSupport).shouldHaveNoInteractions();
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
                given(withdrawnStudentRedisUtil.isWithdrawnRecently("20210001")).willReturn(false);
                given(userRepository.findByStudentId("20210001")).willReturn(Optional.empty());
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

                then(userRepository).shouldHaveNoInteractions();
            }
        }

        @Nested
        @DisplayName("탈퇴 후 30일이 지나지 않은 사용자가 로그인할 때")
        class Context_with_recently_withdrawn_user {

            @Test
            @DisplayName("ExpectedException이 발생하고 FORBIDDEN 상태를 반환해야 한다")
            void it_throws_expected_exception_with_forbidden() {
                // Given
                var reqDto = createReqDto();

                given(oauthClient.exchangeCodeForToken("auth-code-123", "https://example.com/callback"))
                        .willReturn(tokenResponse);
                given(tokenResponse.getAccessToken()).willReturn("oauth-access-token");
                given(oauthClient.getUserInfo("oauth-access-token")).willReturn(userInfoResponse);
                given(userInfoResponse.getStudent()).willReturn(student);
                given(student.getStudentNumber()).willReturn(20210001);
                given(withdrawnStudentRedisUtil.isWithdrawnRecently("20210001")).willReturn(true);

                // When & Then
                assertThatThrownBy(() -> signInService.execute(reqDto)).isInstanceOf(ExpectedException.class)
                        .hasMessage("탈퇴 후 30일이 지나지 않아 재가입할 수 없습니다.")
                        .satisfies(e -> assertThat(((ExpectedException) e).getStatusCode())
                                .isEqualTo(HttpStatus.FORBIDDEN));

                then(userRepository).shouldHaveNoInteractions();
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
                given(withdrawnStudentRedisUtil.isWithdrawnRecently("20210001")).willReturn(false);
                given(userRepository.findByStudentId("20210001")).willReturn(Optional.empty())
                        .willReturn(Optional.of(user));
                given(userRegistrationSupport.register(student)).willThrow(new DataIntegrityViolationException("중복"));
                given(tokenGenerationSupport.generate(user.getId(), user.getRole())).willReturn(expectedTokens);

                // When
                var result = signInService.execute(reqDto);

                // Then
                assertThat(result).isNotNull();
                then(userRepository).should(times(2)).findByStudentId("20210001");
            }
        }
    }
}
