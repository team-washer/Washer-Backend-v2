package team.washer.server.v2.domain.notification.support;

import static org.mockito.BDDMockito.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import team.washer.server.v2.domain.machine.entity.Machine;
import team.washer.server.v2.domain.machine.enums.MachineType;
import team.washer.server.v2.domain.notification.entity.Notification;
import team.washer.server.v2.domain.notification.repository.NotificationRepository;
import team.washer.server.v2.domain.user.entity.User;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReservationNotificationSupport 클래스의")
class ReservationNotificationSupportTest {

    @InjectMocks
    private ReservationNotificationSupport reservationNotificationSupport;

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private FcmNotificationSupport fcmNotificationSupport;

    private User createUser() {
        return User.builder().name("김철수").studentId("20210001").roomNumber("301").grade(3).floor(3).build();
    }

    private Machine createMachine() {
        return Machine.builder().name("WASHER-3F-L1").type(MachineType.WASHER).floor(3).build();
    }

    @Nested
    @DisplayName("알림 저장 후 30개 제한 로직은")
    class Describe_notification_limit {

        @Nested
        @DisplayName("알림 개수가 30개 이하일 때")
        class Context_with_count_under_limit {

            @Test
            @DisplayName("초과 알림 삭제를 수행하지 않아야 한다")
            void it_does_not_delete_excess() {
                // Given
                User user = createUser();
                Machine machine = createMachine();
                given(notificationRepository.save(any(Notification.class)))
                        .willAnswer(invocation -> invocation.getArgument(0));
                given(notificationRepository.countByUser(user)).willReturn(25L);

                // When
                reservationNotificationSupport.sendCompletion(user, machine);

                // Then
                then(notificationRepository).should(never()).deleteOldestByUserExceedingLimit(anyLong(), anyInt());
            }
        }

        @Nested
        @DisplayName("알림 개수가 정확히 30개일 때")
        class Context_with_count_at_limit {

            @Test
            @DisplayName("초과 알림 삭제를 수행하지 않아야 한다")
            void it_does_not_delete_excess() {
                // Given
                User user = createUser();
                Machine machine = createMachine();
                given(notificationRepository.save(any(Notification.class)))
                        .willAnswer(invocation -> invocation.getArgument(0));
                given(notificationRepository.countByUser(user)).willReturn(30L);

                // When
                reservationNotificationSupport.sendCompletion(user, machine);

                // Then
                then(notificationRepository).should(never()).deleteOldestByUserExceedingLimit(anyLong(), anyInt());
            }
        }

        @Nested
        @DisplayName("알림 개수가 30개를 초과할 때")
        class Context_with_count_over_limit {

            @Test
            @DisplayName("초과 알림을 삭제해야 한다")
            void it_deletes_excess_notifications() {
                // Given
                User user = createUser();
                Machine machine = createMachine();
                given(notificationRepository.save(any(Notification.class)))
                        .willAnswer(invocation -> invocation.getArgument(0));
                given(notificationRepository.countByUser(user)).willReturn(31L);
                given(notificationRepository.deleteOldestByUserExceedingLimit(user.getId(), 30)).willReturn(1);

                // When
                reservationNotificationSupport.sendCompletion(user, machine);

                // Then
                then(notificationRepository).should(times(1)).deleteOldestByUserExceedingLimit(user.getId(), 30);
            }
        }
    }
}
