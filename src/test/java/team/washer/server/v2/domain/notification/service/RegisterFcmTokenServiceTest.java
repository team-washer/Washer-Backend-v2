package team.washer.server.v2.domain.notification.service;

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
import org.springframework.http.HttpStatus;

import team.themoment.sdk.exception.ExpectedException;
import team.washer.server.v2.domain.notification.service.impl.RegisterFcmTokenServiceImpl;
import team.washer.server.v2.domain.user.entity.User;
import team.washer.server.v2.domain.user.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName("RegisterFcmTokenServiceImpl 클래스의")
class RegisterFcmTokenServiceTest {

    @InjectMocks
    private RegisterFcmTokenServiceImpl registerFcmTokenService;

    @Mock
    private UserRepository userRepository;

    private User createUser() {
        return User.builder().name("김철수").studentId("20210001").roomNumber("301").grade(3).floor(3).build();
    }

    @Nested
    @DisplayName("execute 메서드는")
    class Describe_execute {

        @Nested
        @DisplayName("FCM 토큰이 없는 사용자에게 토큰을 등록할 때")
        class Context_with_no_existing_token {

            @Test
            @DisplayName("토큰이 등록되어야 한다")
            void it_registers_token() {
                // Given
                Long userId = 1L;
                String token = "new-fcm-token";
                User user = createUser();

                given(userRepository.findById(userId)).willReturn(Optional.of(user));

                // When
                registerFcmTokenService.execute(userId, token);

                // Then
                assertThat(user.getFcmToken()).isEqualTo(token);
                then(userRepository).should(times(1)).findById(userId);
            }
        }

        @Nested
        @DisplayName("기존 FCM 토큰이 있는 사용자의 토큰을 갱신할 때")
        class Context_with_existing_token {

            @Test
            @DisplayName("토큰이 새 값으로 갱신되어야 한다")
            void it_updates_token() {
                // Given
                Long userId = 1L;
                String newToken = "updated-fcm-token";
                User user = createUser();
                user.updateFcmToken("old-fcm-token");

                given(userRepository.findById(userId)).willReturn(Optional.of(user));

                // When
                registerFcmTokenService.execute(userId, newToken);

                // Then
                assertThat(user.getFcmToken()).isEqualTo(newToken);
                then(userRepository).should(times(1)).findById(userId);
            }
        }

        @Nested
        @DisplayName("존재하지 않는 사용자 ID로 요청할 때")
        class Context_with_nonexistent_user_id {

            @Test
            @DisplayName("ExpectedException을 던져야 한다")
            void it_throws_expected_exception() {
                // Given
                Long userId = 999L;

                given(userRepository.findById(userId)).willReturn(Optional.empty());

                // When & Then
                assertThatThrownBy(() -> registerFcmTokenService.execute(userId, "some-token"))
                        .isInstanceOf(ExpectedException.class).hasMessage("사용자를 찾을 수 없습니다")
                        .hasFieldOrPropertyWithValue("statusCode", HttpStatus.NOT_FOUND);

                then(userRepository).should(times(1)).findById(userId);
            }
        }
    }
}
