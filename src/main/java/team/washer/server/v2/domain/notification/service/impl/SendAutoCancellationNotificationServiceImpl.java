package team.washer.server.v2.domain.notification.service.impl;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import team.washer.server.v2.domain.machine.entity.Machine;
import team.washer.server.v2.domain.notification.entity.Notification;
import team.washer.server.v2.domain.notification.enums.NotificationType;
import team.washer.server.v2.domain.notification.repository.NotificationRepository;
import team.washer.server.v2.domain.notification.service.SendAutoCancellationNotificationService;
import team.washer.server.v2.domain.notification.service.SendFcmNotificationService;
import team.washer.server.v2.domain.user.entity.User;

@Service
@RequiredArgsConstructor
@Slf4j
public class SendAutoCancellationNotificationServiceImpl implements SendAutoCancellationNotificationService {

    private final NotificationRepository notificationRepository;
    private final SendFcmNotificationService sendFcmNotificationService;

    @Override
    public void execute(User user, Machine machine) {
        var notification = Notification.createAutoCancellationNotification(user, machine);
        notificationRepository.save(notification);
        log.info("예약 자동 취소 알림 저장 완료 - 사용자: {}, 기기: {}", user.getId(), machine.getName());

        final var fcmTitle = "예약 자동 취소 알림";
        final var fcmBody = NotificationType.AUTO_CANCELLED.formatMessage(machine.getName());
        sendFcmNotificationService.execute(user, fcmTitle, fcmBody);
    }
}
