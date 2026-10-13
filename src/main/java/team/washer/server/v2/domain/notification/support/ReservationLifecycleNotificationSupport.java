package team.washer.server.v2.domain.notification.support;

import java.time.LocalDateTime;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import team.washer.server.v2.domain.machine.entity.Machine;
import team.washer.server.v2.domain.notification.enums.NotificationType;
import team.washer.server.v2.domain.user.entity.User;
import team.washer.server.v2.global.common.transaction.AfterCommitExecutor;

/**
 * 예약 lifecycle 커밋 이후 알림 저장을 등록하는 지원 컴포넌트.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReservationLifecycleNotificationSupport {

    private final ReservationNotificationSupport reservationNotificationSupport;
    private final AfterCommitExecutor afterCommitExecutor;

    public void sendCompletionAfterCommit(final Long reservationId, final User user, final Machine machine) {
        executeAfterCommit(NotificationType.COMPLETION,
                reservationId,
                user,
                machine,
                () -> reservationNotificationSupport.sendCompletion(user, machine));
    }

    public void sendInterruptionAfterCommit(final Long reservationId, final User user, final Machine machine) {
        executeAfterCommit(NotificationType.INTERRUPTION,
                reservationId,
                user,
                machine,
                () -> reservationNotificationSupport.sendInterruption(user, machine));
    }

    public void sendForceStopAfterCommit(final Long reservationId, final User user, final Machine machine) {
        executeAfterCommit(NotificationType.FORCE_STOPPED,
                reservationId,
                user,
                machine,
                () -> reservationNotificationSupport.sendForceStop(user, machine));
    }

    public void sendPauseTimeoutAfterCommit(final Long reservationId, final User user, final Machine machine) {
        executeAfterCommit(NotificationType.PAUSE_TIMEOUT,
                reservationId,
                user,
                machine,
                () -> reservationNotificationSupport.sendPauseTimeout(user, machine));
    }

    public void sendStartedAfterCommit(final Long reservationId,
            final User user,
            final Machine machine,
            final LocalDateTime expectedCompletionTime) {
        executeAfterCommit(NotificationType.STARTED,
                reservationId,
                user,
                machine,
                () -> reservationNotificationSupport.sendStarted(user, machine, expectedCompletionTime));
    }

    private void executeAfterCommit(final NotificationType notificationType,
            final Long reservationId,
            final User user,
            final Machine machine,
            final Runnable notification) {
        afterCommitExecutor.execute(() -> {
            try {
                notification.run();
            } catch (RuntimeException e) {
                log.error(
                        "lifecycle notification persistence failed notificationType={} reservationId={} machineId={} userId={} exceptionType={}",
                        notificationType,
                        reservationId,
                        machine.getId(),
                        user.getId(),
                        e.getClass().getSimpleName(),
                        e);
            }
        });
    }
}
