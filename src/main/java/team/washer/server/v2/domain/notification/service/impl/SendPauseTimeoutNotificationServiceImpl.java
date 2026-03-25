package team.washer.server.v2.domain.notification.service.impl;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import team.washer.server.v2.domain.machine.entity.Machine;
import team.washer.server.v2.domain.notification.entity.Notification;
import team.washer.server.v2.domain.notification.enums.NotificationType;
import team.washer.server.v2.domain.notification.repository.NotificationRepository;
import team.washer.server.v2.domain.notification.service.SendFcmNotificationService;
import team.washer.server.v2.domain.notification.service.SendPauseTimeoutNotificationService;
import team.washer.server.v2.domain.user.entity.User;

@Service
@RequiredArgsConstructor
@Slf4j
public class SendPauseTimeoutNotificationServiceImpl implements SendPauseTimeoutNotificationService {

    private final NotificationRepository notificationRepository;
    private final SendFcmNotificationService sendFcmNotificationService;

    @Override
    public void execute(User user, Machine machine) {
        var notification = Notification.createPauseTimeoutNotification(user, machine);
        notificationRepository.save(notification);
        log.info("일시정지 초과 알림 저장 완료 - 사용자: {}, 기기: {}", user.getId(), machine.getName());

        final var fcmTitle = machine.getType().getDescription() + " 일시정지 초과 알림";
        final var fcmBody = NotificationType.PAUSE_TIMEOUT.formatMessage(machine.getName(), machine.getType());
        sendFcmNotificationService.execute(user, fcmTitle, fcmBody);
    }
}
