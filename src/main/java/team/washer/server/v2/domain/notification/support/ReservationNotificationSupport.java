package team.washer.server.v2.domain.notification.support;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import team.washer.server.v2.domain.machine.entity.Machine;
import team.washer.server.v2.domain.notification.entity.Notification;
import team.washer.server.v2.domain.notification.enums.NotificationType;
import team.washer.server.v2.domain.notification.repository.NotificationRepository;
import team.washer.server.v2.domain.user.entity.User;

/**
 * 예약 관련 알림 전송을 담당하는 지원 컴포넌트.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReservationNotificationSupport {

    private final NotificationRepository notificationRepository;
    private final FcmNotificationSupport fcmNotificationSupport;

    /**
     * 세탁/건조 완료 알림을 전송한다.
     */
    @Transactional
    public void sendCompletion(User user, Machine machine) {
        var notification = Notification.createCompletionNotification(user, machine);
        notificationRepository.save(notification);
        log.info("Completion notification sent to user {} for machine {}", user.getId(), machine.getName());

        final var fcmTitle = machine.getType().getDescription() + " 완료 알림";
        final var fcmBody = NotificationType.COMPLETION.formatMessage(machine.getName(), machine.getType());
        fcmNotificationSupport.send(user, fcmTitle, fcmBody);
    }

    /**
     * 세탁/건조 중단 알림을 전송한다.
     */
    @Transactional
    public void sendInterruption(User user, Machine machine) {
        var notification = Notification.createInterruptionNotification(user, machine);
        notificationRepository.save(notification);
        log.info("세탁 중단 알림 저장 완료 - 사용자: {}, 기기: {}", user.getId(), machine.getName());

        final var fcmTitle = machine.getType().getDescription() + " 중단 알림";
        final var fcmBody = NotificationType.INTERRUPTION.formatMessage(machine.getName(), machine.getType());
        fcmNotificationSupport.send(user, fcmTitle, fcmBody);
    }

    /**
     * 일시정지 초과 알림을 전송한다.
     */
    @Transactional
    public void sendPauseTimeout(User user, Machine machine) {
        var notification = Notification.createPauseTimeoutNotification(user, machine);
        notificationRepository.save(notification);
        log.info("일시정지 초과 알림 저장 완료 - 사용자: {}, 기기: {}", user.getId(), machine.getName());

        final var fcmTitle = machine.getType().getDescription() + " 일시정지 초과 알림";
        final var fcmBody = NotificationType.PAUSE_TIMEOUT.formatMessage(machine.getName(), machine.getType());
        fcmNotificationSupport.send(user, fcmTitle, fcmBody);
    }

    /**
     * 예약 자동 취소 알림을 전송한다.
     */
    @Transactional
    public void sendAutoCancellation(User user, Machine machine) {
        var notification = Notification.createAutoCancellationNotification(user, machine);
        notificationRepository.save(notification);
        log.info("예약 자동 취소 알림 저장 완료 - 사용자: {}, 기기: {}", user.getId(), machine.getName());

        final var fcmTitle = "예약 자동 취소 알림";
        final var fcmBody = NotificationType.AUTO_CANCELLED.formatMessage(machine.getName());
        fcmNotificationSupport.send(user, fcmTitle, fcmBody);
    }
}
