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
import team.washer.server.v2.domain.notification.service.impl.DeleteFcmTokenServiceImpl;
import team.washer.server.v2.domain.user.entity.User;
import team.washer.server.v2.domain.user.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName("DeleteFcmTokenServiceImpl 클래스의")
class DeleteFcmTokenServiceTest {

    @InjectMocks
    private DeleteFcmTokenServiceImpl deleteFcmTokenService;

    @Mock
    private UserRepository userRepository;

    private User createUserWithToken(String token) {
        User user = User.builder().name("김철수").studentId("20210001").roomNumber("301").grade(3).floor(3).build();
        user.updateFcmToken(token);
        return user;
    }

    @Nested
    @DisplayName("execute 메서드는")
    class Describe_execute {

        @Nested
        @DisplayName("FCM 토큰이 있는 사용자의 토큰을 삭제할 때")
        class Context_with_existing_token {

            @Test
            @DisplayName("FCM 토큰이 null로 초기화되어야 한다")
            void it_clears_token() {
                // Given
                Long userId = 1L;
                User user = createUserWithToken("existing-fcm-token");

                given(userRepository.findById(userId)).willReturn(Optional.of(user));

                // When
                deleteFcmTokenService.execute(userId);

                // Then
                assertThat(user.getFcmToken()).isNull();
                then(userRepository).should(times(1)).findById(userId);
            }
        }

        @Nested
        @DisplayName("FCM 토큰이 없는 사용자의 토큰 삭제를 요청할 때")
        class Context_with_no_existing_token {

            @Test
            @DisplayName("FCM 토큰이 null로 유지되어야 한다")
            void it_remains_null() {
                // Given
                Long userId = 1L;
                User user = User.builder().name("김철수").studentId("20210001").roomNumber("301").grade(3).floor(3)
                        .build();

                given(userRepository.findById(userId)).willReturn(Optional.of(user));

                // When
                deleteFcmTokenService.execute(userId);

                // Then
                assertThat(user.getFcmToken()).isNull();
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
                assertThatThrownBy(() -> deleteFcmTokenService.execute(userId)).isInstanceOf(ExpectedException.class)
                        .hasMessage("사용자를 찾을 수 없습니다").hasFieldOrPropertyWithValue("statusCode", HttpStatus.NOT_FOUND);

                then(userRepository).should(times(1)).findById(userId);
            }
        }
    }
}
