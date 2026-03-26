package team.washer.server.v2.domain.notification.support;

import java.time.LocalDateTime;

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
        log.info("Interruption notification sent to user {} for machine {}", user.getId(), machine.getName());

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
        log.info("Pause timeout notification sent to user {} for machine {}", user.getId(), machine.getName());

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
        log.info("Auto cancellation notification sent to user {} for machine {}", user.getId(), machine.getName());

        final var fcmTitle = "예약 자동 취소 알림";
        final var fcmBody = NotificationType.AUTO_CANCELLED.formatMessage(machine.getName());
        fcmNotificationSupport.send(user, fcmTitle, fcmBody);
    }

    /**
     * 세탁/건조 시작 알림을 전송한다.
     */
    @Transactional
    public void sendStarted(User user, Machine machine, LocalDateTime expectedCompletionTime) {
        var notification = Notification.createStartedNotification(user, machine, expectedCompletionTime);
        notificationRepository.save(notification);
        log.info("Start notification sent to user {} for machine {}", user.getId(), machine.getName());

        final var fcmTitle = machine.getType().getDescription() + " 시작 알림";
        fcmNotificationSupport.send(user, fcmTitle, notification.getMessage());
    }

    /**
     * 예약 취소 경고 알림(첫 번째 타임아웃)을 전송한다.
     */
    @Transactional
    public void sendTimeoutWarning(User user, Machine machine) {
        var notification = Notification.createTimeoutWarningNotification(user, machine);
        notificationRepository.save(notification);
        log.info("Timeout warning notification sent to user {} for machine {}", user.getId(), machine.getName());

        final var fcmTitle = "예약 취소 경고";
        fcmNotificationSupport.send(user, fcmTitle, notification.getMessage());
    }
}
