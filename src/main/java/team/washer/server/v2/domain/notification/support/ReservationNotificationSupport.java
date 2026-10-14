package team.washer.server.v2.domain.notification.support;

import java.time.LocalDateTime;

import org.hibernate.Hibernate;
import org.hibernate.proxy.HibernateProxy;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import lombok.extern.slf4j.Slf4j;
import team.washer.server.v2.domain.machine.entity.Machine;
import team.washer.server.v2.domain.notification.entity.Notification;
import team.washer.server.v2.domain.notification.enums.NotificationType;
import team.washer.server.v2.domain.notification.repository.NotificationRepository;
import team.washer.server.v2.domain.user.entity.User;
import team.washer.server.v2.global.common.entity.BaseEntity;
import team.washer.server.v2.global.common.transaction.AfterCommitExecutor;

/**
 * 예약 관련 알림 전송을 담당하는 지원 컴포넌트.
 *
 * <p>
 * 예약 lifecycle 전이 알림(시작·완료·중단·일시정지 초과)과 관리자 강제 정지 알림은 호출자의 트랜잭션과 분리한다. 알림 내용은
 * 호출 시점에 확정하고, 저장과 보관 개수 정리는 호출자 트랜잭션이 커밋된 뒤 독립 트랜잭션에서 수행한다. 호출 시점의 알림 준비(사용자
 * 초기화·내용 생성)가 실패해도 예외를 호출자에게 전파하지 않는다. 따라서 알림 실패가 예약 취소를 포함한 예약·기기 상태 전이를 롤백시키지
 * 않는다.
 * </p>
 *
 * <p>
 * 분리된 알림의 유실·중복 정책은 다음과 같다.
 * </p>
 * <ul>
 * <li>중복: 상태 전이는 행 락과 상태 재검증으로 한 번만 커밋되므로 같은 전이의 알림은 최대 한 번 저장·전송된다.</li>
 * <li>유실: 준비, 저장, 보관 개수 정리 중 하나라도 실패한 알림은 재시도하지 않으며 FCM도 전송하지 않는다. 호출자 트랜잭션이
 * 롤백되면 알림도 만들지 않는다.</li>
 * <li>관측: 실패는 {@code event=notification_persist_failed} 로그로 남기고 {@code phase}로
 * 준비 단계({@code prepare})와 저장 단계({@code persist})를 구분한다. 어떤 예약 전이의 알림이 유실됐는지 특정할
 * 수 있도록 {@code reservationId}를 함께 남긴다. CloudWatch 메트릭 필터로 집계할 수 있도록 고정된 키를
 * 사용한다.</li>
 * </ul>
 */
@Slf4j
@Component
public class ReservationNotificationSupport {

    private static final int MAX_NOTIFICATIONS_PER_USER = 30;

    private final NotificationRepository notificationRepository;
    private final FcmNotificationSupport fcmNotificationSupport;
    private final AfterCommitExecutor afterCommitExecutor;
    private final TransactionTemplate isolatedTransactionTemplate;

    public ReservationNotificationSupport(final NotificationRepository notificationRepository,
            final FcmNotificationSupport fcmNotificationSupport,
            final AfterCommitExecutor afterCommitExecutor,
            final PlatformTransactionManager transactionManager) {
        this.notificationRepository = notificationRepository;
        this.fcmNotificationSupport = fcmNotificationSupport;
        this.afterCommitExecutor = afterCommitExecutor;
        this.isolatedTransactionTemplate = new TransactionTemplate(transactionManager);
        this.isolatedTransactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * 세탁/건조 완료 알림을 전송한다. 호출자 트랜잭션과 분리해 커밋 이후에 저장한다.
     */
    public void sendCompletion(final Long reservationId, final User user, final Machine machine) {
        isolateFromCaller(reservationId, user, machine, NotificationType.COMPLETION, () -> {
            var notification = Notification.createCompletionNotification(user, machine);
            persistAndSendAfterCommit(reservationId, user, notification, machine.getType().getDescription() + " 완료 알림");
        });
    }

    /**
     * 세탁/건조 중단 알림을 전송한다. 호출자 트랜잭션과 분리해 커밋 이후에 저장한다.
     */
    public void sendInterruption(final Long reservationId, final User user, final Machine machine) {
        isolateFromCaller(reservationId, user, machine, NotificationType.INTERRUPTION, () -> {
            var notification = Notification.createInterruptionNotification(user, machine);
            persistAndSendAfterCommit(reservationId, user, notification, machine.getType().getDescription() + " 중단 알림");
        });
    }

    /**
     * 관리자 강제 정지에 의한 예약 취소 알림을 전송한다. 호출자 트랜잭션과 분리해 커밋 이후에 저장한다.
     */
    public void sendForceStop(final Long reservationId, final User user, final Machine machine) {
        isolateFromCaller(reservationId, user, machine, NotificationType.FORCE_STOPPED, () -> {
            var notification = Notification.createForceStopNotification(user, machine);
            persistAndSendAfterCommit(reservationId,
                    user,
                    notification,
                    machine.getType().getDescription() + " 강제 정지 알림");
        });
    }

    /**
     * 일시정지 초과 알림을 전송한다. 호출자 트랜잭션과 분리해 커밋 이후에 저장한다.
     */
    public void sendPauseTimeout(final Long reservationId, final User user, final Machine machine) {
        isolateFromCaller(reservationId, user, machine, NotificationType.PAUSE_TIMEOUT, () -> {
            var notification = Notification.createPauseTimeoutNotification(user, machine);
            persistAndSendAfterCommit(reservationId,
                    user,
                    notification,
                    machine.getType().getDescription() + " 일시정지 초과 알림");
        });
    }

    /**
     * 예약 자동 취소 알림을 전송한다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void sendAutoCancellation(User user, Machine machine) {
        var notification = Notification.createAutoCancellationNotification(user, machine);
        persistAndSend(user, notification, "예약 자동 취소 알림");
    }

    /**
     * 세탁/건조 시작 알림을 전송한다. 호출자 트랜잭션과 분리해 커밋 이후에 저장한다.
     */
    public void sendStarted(final Long reservationId,
            final User user,
            final Machine machine,
            final LocalDateTime expectedCompletionTime) {
        isolateFromCaller(reservationId, user, machine, NotificationType.STARTED, () -> {
            var notification = Notification.createStartedNotification(user, machine, expectedCompletionTime);
            persistAndSendAfterCommit(reservationId, user, notification, machine.getType().getDescription() + " 시작 알림");
        });
    }

    /**
     * 예약 취소 경고 알림(첫 번째 타임아웃)을 전송한다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void sendTimeoutWarning(User user, Machine machine) {
        var notification = Notification.createTimeoutWarningNotification(user, machine);
        persistAndSend(user, notification, "예약 취소 경고");
    }

    /**
     * 48시간 예약 차단 알림을 전송한다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void sendCancellationBlock(User user, Machine machine) {
        var notification = Notification.createCancellationBlockNotification(user, machine);
        persistAndSend(user, notification, "예약 차단 알림");
    }

    /**
     * 예약 차단 연장 알림을 전송한다.
     */
    @Transactional
    public void sendBlockExtension(User user, LocalDateTime newExpiryAt) {
        var notification = Notification.createBlockExtensionNotification(user, newExpiryAt);
        persistAndSend(user, notification, "예약 차단 알림");
    }

    /**
     * 관리자 패널티 부과 알림을 전송한다.
     */
    @Transactional
    public void sendAdminPenalty(User user, String reason) {
        var notification = Notification.createAdminPenaltyNotification(user, reason);
        persistAndSend(user, notification, "예약 차단 알림");
    }

    /**
     * 호출 시점에 수행하는 알림 준비(사용자 초기화·내용 생성)의 실패를 호출자에게 전파하지 않는다.
     *
     * <p>
     * 준비 단계는 호출자 트랜잭션 안에서 실행되므로, 여기서 던진 예외는 예약·기기 상태 전이를 롤백시킨다. 실패한 알림은 저장하지 않고
     * 로그로만 남긴다.
     * </p>
     */
    private void isolateFromCaller(final Long reservationId,
            final User user,
            final Machine machine,
            final NotificationType type,
            final Runnable preparation) {
        try {
            preparation.run();
        } catch (RuntimeException e) {
            logPersistFailure("prepare", reservationId, idOf(user), type, idOf(machine), e);
        }
    }

    /**
     * 호출자 트랜잭션이 커밋된 뒤 독립 트랜잭션에서 알림을 저장하고 FCM 전송을 예약한다. 호출자 트랜잭션이 롤백되면 실행하지 않고, 트랜잭션
     * 밖에서는 즉시 실행한다.
     *
     * <p>
     * 저장 실패는 예외로 전파하지 않는다. 커밋 콜백에서 던진 예외는 이미 커밋된 호출자에게 실패로 전달되어 후속 처리(완료 후 전원 차단
     * 등)를 끊기 때문이다.
     * </p>
     */
    private void persistAndSendAfterCommit(final Long reservationId,
            final User user,
            final Notification notification,
            final String fcmTitle) {
        // 커밋 이후에는 호출자의 영속성 컨텍스트를 쓸 수 없으므로 FCM 토큰을 읽을 사용자를 미리 초기화한다
        Hibernate.initialize(user);
        afterCommitExecutor.execute(() -> {
            try {
                isolatedTransactionTemplate
                        .executeWithoutResult(status -> persistAndSend(user, notification, fcmTitle));
            } catch (RuntimeException e) {
                logPersistFailure("persist",
                        reservationId,
                        idOf(user),
                        notification.getType(),
                        idOf(notification.getMachine()),
                        e);
            }
        });
    }

    private void logPersistFailure(final String phase,
            final Long reservationId,
            final Object userId,
            final NotificationType type,
            final Object machineId,
            final RuntimeException e) {
        log.error(
                "notification persist failed event=notification_persist_failed reservationId={} userId={} type={} machineId={} errorType={} phase={}",
                reservationId,
                userId,
                type,
                machineId,
                e.getClass().getSimpleName(),
                phase,
                e);
    }

    /**
     * 초기화에 실패한 프록시는 식별자 getter 호출만으로도 다시 예외를 던지므로 프록시를 초기화하지 않고 식별자를 읽는다.
     */
    private static Object idOf(final BaseEntity entity) {
        if (entity == null) {
            return null;
        }
        final var lazyInitializer = HibernateProxy.extractLazyInitializer(entity);
        return lazyInitializer != null ? lazyInitializer.getInternalIdentifier() : entity.getId();
    }

    private void persistAndSend(final User user, final Notification notification, final String fcmTitle) {
        notificationRepository.save(notification);
        enforceNotificationLimit(user);
        log.info("Notification persisted userId={} type={}", user.getId(), notification.getType());
        afterCommitExecutor.execute(() -> sendFcm(user, notification, fcmTitle));
    }

    private void sendFcm(final User user, final Notification notification, final String fcmTitle) {
        try {
            fcmNotificationSupport.send(user, fcmTitle, notification.getMessage());
        } catch (RuntimeException e) {
            log.error("FCM notification send failed userId={} type={}", user.getId(), notification.getType(), e);
        }
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
