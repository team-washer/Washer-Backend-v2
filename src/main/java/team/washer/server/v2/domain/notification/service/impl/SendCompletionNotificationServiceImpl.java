package team.washer.server.v2.domain.notification.service.impl;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import team.washer.server.v2.domain.machine.entity.Machine;
import team.washer.server.v2.domain.notification.entity.Notification;
import team.washer.server.v2.domain.notification.enums.NotificationType;
import team.washer.server.v2.domain.notification.repository.NotificationRepository;
import team.washer.server.v2.domain.notification.service.SendCompletionNotificationService;
import team.washer.server.v2.domain.notification.service.SendFcmNotificationService;
import team.washer.server.v2.domain.user.entity.User;

@Service
@RequiredArgsConstructor
@Slf4j
public class SendCompletionNotificationServiceImpl implements SendCompletionNotificationService {

    private final NotificationRepository notificationRepository;
    private final SendFcmNotificationService sendFcmNotificationService;

    @Override
    public void execute(User user, Machine machine) {
        var notification = Notification.createCompletionNotification(user, machine);
        notificationRepository.save(notification);
        log.info("Completion notification sent to user {} for machine {}", user.getId(), machine.getName());

        final var fcmTitle = machine.getType().getDescription() + " 완료 알림";
        final var fcmBody = NotificationType.COMPLETION.formatMessage(machine.getName(), machine.getType());
        sendFcmNotificationService.execute(user, fcmTitle, fcmBody);
    }
}
