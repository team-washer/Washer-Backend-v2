package team.washer.server.v2.domain.notification.service;

import static org.mockito.ArgumentMatchers.any;
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
import team.washer.server.v2.domain.notification.entity.Notification;
import team.washer.server.v2.domain.notification.repository.NotificationRepository;
import team.washer.server.v2.domain.notification.service.impl.SendCompletionNotificationServiceImpl;
import team.washer.server.v2.domain.user.entity.User;

@ExtendWith(MockitoExtension.class)
class SendCompletionNotificationServiceTest {

    @InjectMocks
    private SendCompletionNotificationServiceImpl sendCompletionNotificationService;

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private User user;

    @Mock
    private Machine machine;

    @Nested
    @DisplayName("세탁 완료 알림 전송")
    class ExecuteTest {

        @Test
        @DisplayName("세탁 완료 알림을 성공적으로 전송한다")
        void execute_ShouldSendNotification_WhenValidUserAndMachine() {
            // Given
            when(machine.getName()).thenReturn("W-3F-L1");

            // When
            sendCompletionNotificationService.execute(user, machine);

            // Then
            verify(notificationRepository).save(any(Notification.class));
        }

        @Test
        @DisplayName("알림 전송 실패 시 예외를 로그로 처리한다")
        void execute_ShouldLogException_WhenNotificationFails() {
            // Given
            when(machine.getName()).thenReturn("W-3F-A1");
            when(notificationRepository.save(any(Notification.class)))
                    .thenThrow(new RuntimeException("Database error"));

            // When & Then - 예외가 전파되지 않고 로그로 처리됨
            sendCompletionNotificationService.execute(user, machine);
        }
    }
}
