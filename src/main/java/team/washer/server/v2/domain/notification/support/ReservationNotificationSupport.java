package team.washer.server.v2.domain.notification.support;

import java.time.LocalDateTime;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import team.washer.server.v2.domain.machine.entity.Machine;
import team.washer.server.v2.domain.notification.entity.Notification;
import team.washer.server.v2.domain.notification.repository.NotificationRepository;
import team.washer.server.v2.domain.user.entity.User;

/**
 * 예약 관련 알림 전송을 담당하는 지원 컴포넌트.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReservationNotificationSupport {

    private static final int MAX_NOTIFICATIONS_PER_USER = 30;

    private final NotificationRepository notificationRepository;
    private final FcmNotificationSupport fcmNotificationSupport;

    /**
     * 세탁/건조 완료 알림을 전송한다.
     */
    @Transactional
    public void sendCompletion(User user, Machine machine) {
        var notification = Notification.createCompletionNotification(user, machine);
        persistAndSend(user, notification, machine.getType().getDescription() + " 완료 알림");
    }

    /**
     * 세탁/건조 중단 알림을 전송한다.
     */
    @Transactional
    public void sendInterruption(User user, Machine machine) {
        var notification = Notification.createInterruptionNotification(user, machine);
        persistAndSend(user, notification, machine.getType().getDescription() + " 중단 알림");
    }

    /**
     * 일시정지 초과 알림을 전송한다.
     */
    @Transactional
    public void sendPauseTimeout(User user, Machine machine) {
        var notification = Notification.createPauseTimeoutNotification(user, machine);
        persistAndSend(user, notification, machine.getType().getDescription() + " 일시정지 초과 알림");
    }

    /**
     * 예약 자동 취소 알림을 전송한다.
     */
    @Transactional
    public void sendAutoCancellation(User user, Machine machine) {
        var notification = Notification.createAutoCancellationNotification(user, machine);
        persistAndSend(user, notification, "예약 자동 취소 알림");
    }

    /**
     * 세탁/건조 시작 알림을 전송한다.
     */
    @Transactional
    public void sendStarted(User user, Machine machine, LocalDateTime expectedCompletionTime) {
        var notification = Notification.createStartedNotification(user, machine, expectedCompletionTime);
        persistAndSend(user, notification, machine.getType().getDescription() + " 시작 알림");
    }

    /**
     * 예약 취소 경고 알림(첫 번째 타임아웃)을 전송한다.
     */
    @Transactional
    public void sendTimeoutWarning(User user, Machine machine) {
        var notification = Notification.createTimeoutWarningNotification(user, machine);
        persistAndSend(user, notification, "예약 취소 경고");
    }

    private void persistAndSend(final User user, final Notification notification, final String fcmTitle) {
        notificationRepository.save(notification);
        enforceNotificationLimit(user);
        log.info("Notification sent userId={} type={}", user.getId(), notification.getType());
        fcmNotificationSupport.send(user, fcmTitle, notification.getMessage());
    }

    private void enforceNotificationLimit(final User user) {
        final long count = notificationRepository.countByUser(user);
        if (count > MAX_NOTIFICATIONS_PER_USER) {
            final int deleted = notificationRepository.deleteOldestByUserExceedingLimit(user,
                    MAX_NOTIFICATIONS_PER_USER);
            log.info("Excess notifications removed userId={} deletedCount={}", user.getId(), deleted);
        }
    }
}
