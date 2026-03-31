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
import team.washer.server.v2.domain.notification.repository.NotificationRepository;
import team.washer.server.v2.domain.notification.service.impl.DeleteAllNotificationsServiceImpl;
import team.washer.server.v2.domain.user.entity.User;
import team.washer.server.v2.domain.user.repository.UserRepository;
import team.washer.server.v2.global.security.provider.CurrentUserProvider;

@ExtendWith(MockitoExtension.class)
@DisplayName("DeleteAllNotificationsServiceImpl 클래스의")
class DeleteAllNotificationsServiceTest {

    @InjectMocks
    private DeleteAllNotificationsServiceImpl deleteAllNotificationsService;

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private CurrentUserProvider currentUserProvider;

    private static final Long USER_ID = 1L;

    private User createUser() {
        return User.builder().name("김철수").studentId("20210001").roomNumber("301").grade(3).floor(3).build();
    }

    @Nested
    @DisplayName("execute 메서드는")
    class Describe_execute {

        @Nested
        @DisplayName("사용자가 존재할 때")
        class Context_with_existing_user {

            @Test
            @DisplayName("해당 사용자의 모든 알림을 삭제해야 한다")
            void it_deletes_all_notifications() {
                // Given
                when(currentUserProvider.getCurrentUserId()).thenReturn(USER_ID);
                User user = createUser();
                given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));
                given(notificationRepository.deleteAllByUser(user)).willReturn(5);

                // When
                deleteAllNotificationsService.execute();

                // Then
                then(notificationRepository).should(times(1)).deleteAllByUser(user);
            }
        }

        @Nested
        @DisplayName("사용자에게 알림이 없을 때")
        class Context_with_no_notifications {

            @Test
            @DisplayName("삭제 건수가 0이어도 정상 처리되어야 한다")
            void it_succeeds_with_zero_deletions() {
                // Given
                when(currentUserProvider.getCurrentUserId()).thenReturn(USER_ID);
                User user = createUser();
                given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));
                given(notificationRepository.deleteAllByUser(user)).willReturn(0);

                // When
                deleteAllNotificationsService.execute();

                // Then
                then(notificationRepository).should(times(1)).deleteAllByUser(user);
            }
        }

        @Nested
        @DisplayName("존재하지 않는 사용자 ID로 요청할 때")
        class Context_with_nonexistent_user_id {

            @Test
            @DisplayName("ExpectedException을 던져야 한다")
            void it_throws_expected_exception() {
                // Given
                when(currentUserProvider.getCurrentUserId()).thenReturn(USER_ID);
                given(userRepository.findById(USER_ID)).willReturn(Optional.empty());

                // When & Then
                assertThatThrownBy(() -> deleteAllNotificationsService.execute()).isInstanceOf(ExpectedException.class)
                        .hasMessage("사용자를 찾을 수 없습니다").hasFieldOrPropertyWithValue("statusCode", HttpStatus.NOT_FOUND);

                then(notificationRepository).shouldHaveNoInteractions();
            }
        }
    }
}
