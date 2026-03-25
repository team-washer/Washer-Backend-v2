package team.washer.server.v2.domain.notification.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import team.washer.server.v2.domain.notification.service.impl.SendInterruptionNotificationServiceImpl;
import team.washer.server.v2.domain.user.entity.User;

@ExtendWith(MockitoExtension.class)
class SendInterruptionNotificationServiceTest {

    @InjectMocks
    private SendInterruptionNotificationServiceImpl sendInterruptionNotificationService;

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private SendFcmNotificationService sendFcmNotificationService;

    @Mock
    private User user;

    @Mock
    private Machine machine;

    @Nested
    @DisplayName("세탁 중단 알림 전송")
    class ExecuteTest {

        @Test
        @DisplayName("세탁 중단 알림을 DB에 저장하고 FCM으로 전송한다")
        void execute_ShouldSaveNotificationAndSendFcm_WhenValidUserAndMachine() {
            // Given
            when(machine.getName()).thenReturn("W-3F-L1");
            when(machine.getType()).thenReturn(MachineType.DRYER);

            // When
            sendInterruptionNotificationService.execute(user, machine);

            // Then
            verify(notificationRepository).save(any(Notification.class));
            verify(sendFcmNotificationService).execute(any(User.class), anyString(), anyString());
        }
    }
}
