package team.washer.server.v2.domain.notification.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

import java.util.Collections;
import java.util.List;
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
import team.washer.server.v2.domain.notification.dto.response.NotificationListResDto;
import team.washer.server.v2.domain.notification.entity.Notification;
import team.washer.server.v2.domain.notification.enums.NotificationType;
import team.washer.server.v2.domain.notification.repository.NotificationRepository;
import team.washer.server.v2.domain.notification.service.impl.QueryNotificationListServiceImpl;
import team.washer.server.v2.domain.user.entity.User;
import team.washer.server.v2.domain.user.repository.UserRepository;
import team.washer.server.v2.global.security.provider.CurrentUserProvider;

@ExtendWith(MockitoExtension.class)
@DisplayName("QueryNotificationListServiceImpl 클래스의")
class QueryNotificationListServiceTest {

    @InjectMocks
    private QueryNotificationListServiceImpl queryNotificationListService;

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
        @DisplayName("사용자에게 알림이 존재할 때")
        class Context_with_notifications {

            @Test
            @DisplayName("알림 목록을 최신순으로 반환해야 한다")
            void it_returns_notification_list() {
                // Given
                when(currentUserProvider.getCurrentUserId()).thenReturn(USER_ID);
                User user = createUser();
                given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));

                Notification notification1 = Notification.builder().user(user).type(NotificationType.COMPLETION)
                        .message("WASHER-3F-L1의 세탁이 완료되었습니다.").build();
                Notification notification2 = Notification.builder().user(user).type(NotificationType.STARTED)
                        .message("WASHER-3F-L1의 세탁이 시작되었습니다.").build();

                given(notificationRepository.findByUserOrderByCreatedAtDesc(user))
                        .willReturn(List.of(notification1, notification2));

                // When
                NotificationListResDto result = queryNotificationListService.execute();

                // Then
                assertThat(result.notifications()).hasSize(2);
                assertThat(result.notifications().get(0).type()).isEqualTo(NotificationType.COMPLETION);
                assertThat(result.notifications().get(0).message()).isEqualTo("WASHER-3F-L1의 세탁이 완료되었습니다.");
                assertThat(result.notifications().get(1).type()).isEqualTo(NotificationType.STARTED);
                then(notificationRepository).should(times(1)).findByUserOrderByCreatedAtDesc(user);
            }
        }

        @Nested
        @DisplayName("사용자에게 알림이 없을 때")
        class Context_with_no_notifications {

            @Test
            @DisplayName("빈 목록을 반환해야 한다")
            void it_returns_empty_list() {
                // Given
                when(currentUserProvider.getCurrentUserId()).thenReturn(USER_ID);
                User user = createUser();
                given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));
                given(notificationRepository.findByUserOrderByCreatedAtDesc(user)).willReturn(Collections.emptyList());

                // When
                NotificationListResDto result = queryNotificationListService.execute();

                // Then
                assertThat(result.notifications()).isEmpty();
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
                assertThatThrownBy(() -> queryNotificationListService.execute()).isInstanceOf(ExpectedException.class)
                        .hasMessage("사용자를 찾을 수 없습니다").hasFieldOrPropertyWithValue("statusCode", HttpStatus.NOT_FOUND);

                then(notificationRepository).shouldHaveNoInteractions();
            }
        }
    }
}
