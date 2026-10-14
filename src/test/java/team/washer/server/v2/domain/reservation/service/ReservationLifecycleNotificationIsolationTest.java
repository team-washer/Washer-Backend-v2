package team.washer.server.v2.domain.reservation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import java.util.Map;
import java.util.Optional;
import java.util.function.UnaryOperator;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import team.washer.server.v2.domain.machine.entity.Machine;
import team.washer.server.v2.domain.machine.enums.MachineAvailability;
import team.washer.server.v2.domain.machine.enums.MachineType;
import team.washer.server.v2.domain.machine.enums.Position;
import team.washer.server.v2.domain.machine.repository.MachineRepository;
import team.washer.server.v2.domain.notification.enums.NotificationType;
import team.washer.server.v2.domain.notification.repository.NotificationRepository;
import team.washer.server.v2.domain.notification.support.FcmNotificationSupport;
import team.washer.server.v2.domain.notification.support.ReservationNotificationSupport;
import team.washer.server.v2.domain.reservation.entity.Reservation;
import team.washer.server.v2.domain.reservation.enums.ReservationStatus;
import team.washer.server.v2.domain.reservation.repository.ReservationRepository;
import team.washer.server.v2.domain.reservation.service.impl.ReservationLifecycleProcessor;
import team.washer.server.v2.domain.reservation.support.CompletionDecision;
import team.washer.server.v2.domain.reservation.support.ReservationCompletionDecisionSupport;
import team.washer.server.v2.domain.reservation.support.ReservationStartDecisionSupport;
import team.washer.server.v2.domain.smartthings.dto.response.SmartThingsDeviceStatusResDto;
import team.washer.server.v2.domain.smartthings.support.MachineShutdownClaimSupport;
import team.washer.server.v2.domain.smartthings.support.MachineStateDetectionSupport;
import team.washer.server.v2.domain.user.entity.User;
import team.washer.server.v2.domain.user.repository.UserRepository;
import team.washer.server.v2.global.common.constants.ReservationConstants;
import team.washer.server.v2.global.common.transaction.AfterCommitExecutor;
import team.washer.server.v2.global.config.JpaAuditingConfig;
import team.washer.server.v2.global.config.QueryDslConfig;
import team.washer.server.v2.global.util.DateTimeUtil;

/**
 * 예약 lifecycle 전이와 알림 저장의 트랜잭션 경계를 실제 JPA 트랜잭션으로 검증합니다.
 *
 * <p>
 * 알림 저장 실패는 알림 테이블에 모든 삽입을 거부하는 제약을 걸어 재현합니다. 저장 실패가 이미 커밋된 예약·기기 상태 전이를 되돌리지
 * 않고, 같은 전이가 알림을 한 번만 만드는지 확인합니다.
 */
@DataJpaTest
@Import({QueryDslConfig.class, JpaAuditingConfig.class, ReservationLifecycleProcessor.class,
        ReservationNotificationSupport.class, AfterCommitExecutor.class})
@TestPropertySource(properties = {"spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.docker.compose.enabled=false"})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DisplayName("예약 lifecycle 전이와 알림 저장 트랜잭션 분리 통합 테스트")
class ReservationLifecycleNotificationIsolationTest {

    private static final String INSERT_BLOCKING_CONSTRAINT = "notification_insert_blocked";

    @Autowired
    private ReservationLifecycleProcessor reservationLifecycleProcessor;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private MachineRepository machineRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ReservationNotificationSupport reservationNotificationSupport;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @MockitoBean
    private ReservationStartDecisionSupport reservationStartDecisionSupport;

    @MockitoBean
    private ReservationCompletionDecisionSupport completionDecisionSupport;

    @MockitoBean
    private MachineStateDetectionSupport machineStateDetectionSupport;

    @MockitoBean
    private MachineShutdownClaimSupport machineShutdownClaimSupport;

    @MockitoBean
    private FcmNotificationSupport fcmNotificationSupport;

    private User user;
    private Machine machine;

    @BeforeEach
    void setUp() {
        final var newUser = User.builder().name("김세탁").studentId("2101").roomNumber("301").grade(1).floor(3).build();
        newUser.updateFcmToken("fcm-token");
        user = userRepository.save(newUser);
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.execute("ALTER TABLE notifications DROP CONSTRAINT IF EXISTS " + INSERT_BLOCKING_CONSTRAINT);
        notificationRepository.deleteAllInBatch();
        reservationRepository.deleteAllInBatch();
        machineRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    @Nested
    @DisplayName("알림 저장이 성공하면")
    class Context_with_notification_persisted {

        @ParameterizedTest
        @EnumSource(Transition.class)
        @DisplayName("상태 전이를 커밋하고 전이 알림을 한 번 저장·전송해야 한다")
        void commits_transition_and_persists_notification_once(final Transition transition) {
            // Given
            final Long reservationId = givenReservationReadyFor(transition);

            // When
            process(transition, reservationId);

            // Then
            assertTransitionCommitted(transition, reservationId);
            assertThat(notificationRepository.findByUserAndType(user, transition.notificationType)).hasSize(1);
            assertThat(notificationRepository.count()).isEqualTo(1);
            then(fcmNotificationSupport).should(times(1)).send(any(User.class), anyString(), anyString());
        }

        @ParameterizedTest
        @EnumSource(Transition.class)
        @DisplayName("같은 전이를 다시 처리해도 알림을 중복 저장·전송하지 않아야 한다")
        void does_not_duplicate_notification_when_processed_again(final Transition transition) {
            // Given
            final Long reservationId = givenReservationReadyFor(transition);
            process(transition, reservationId);

            // When
            process(transition, reservationId);

            // Then
            assertTransitionCommitted(transition, reservationId);
            assertThat(notificationRepository.count()).isEqualTo(1);
            then(fcmNotificationSupport).should(times(1)).send(any(User.class), anyString(), anyString());
        }
    }

    @Nested
    @DisplayName("알림 저장이 실패하면")
    class Context_with_notification_persist_failure {

        @ParameterizedTest
        @EnumSource(Transition.class)
        @DisplayName("예외 없이 예약·기기 상태 전이를 커밋하고 알림과 FCM은 남기지 않아야 한다")
        void commits_transition_without_notification(final Transition transition) {
            // Given
            final Long reservationId = givenReservationReadyFor(transition);
            blockNotificationInsert();

            // When & Then
            assertThatCode(() -> process(transition, reservationId)).doesNotThrowAnyException();
            assertTransitionCommitted(transition, reservationId);
            assertThat(notificationRepository.count()).isZero();
            then(fcmNotificationSupport).should(never()).send(any(User.class), anyString(), anyString());
        }

        @Test
        @DisplayName("완료 전이는 전원 차단 대상 기기를 그대로 반환해야 한다")
        void returns_completed_machine_for_shutdown() {
            // Given
            final Long reservationId = givenReservationReadyFor(Transition.COMPLETION);
            blockNotificationInsert();

            // When
            final var result = reservationLifecycleProcessor.processRunningToCompleted(reservationId, deviceStatus());

            // Then
            assertThat(result).hasValueSatisfying(completedMachine -> {
                assertThat(completedMachine.machineId()).isEqualTo(machine.getId());
                assertThat(completedMachine.shutdownClaimToken()).isEqualTo("claim-token");
            });
        }

        @ParameterizedTest
        @EnumSource(Transition.class)
        @DisplayName("이미 커밋된 전이를 다시 처리해도 유실된 알림을 재시도하지 않아야 한다")
        void does_not_retry_lost_notification(final Transition transition) {
            // Given
            final Long reservationId = givenReservationReadyFor(transition);
            blockNotificationInsert();
            process(transition, reservationId);
            unblockNotificationInsert();

            // When
            process(transition, reservationId);

            // Then
            assertTransitionCommitted(transition, reservationId);
            assertThat(notificationRepository.count()).isZero();
            then(fcmNotificationSupport).should(never()).send(any(User.class), anyString(), anyString());
        }
    }

    /**
     * lifecycle 처리기는 사용자를 미리 조회해 넘기므로 초기화 실패를 처리기 경로로는 재현할 수 없습니다. 그래서 상태 전이 트랜잭션
     * 안에서 초기화할 수 없는 사용자 프록시로 알림을 직접 호출합니다.
     */
    @Nested
    @DisplayName("알림 준비 단계에서 사용자 초기화가 실패하면")
    class Context_with_user_initialization_failure {

        @ParameterizedTest
        @EnumSource(Transition.class)
        @DisplayName("예외 없이 같은 트랜잭션의 기기 상태 전이를 커밋하고 알림과 FCM은 남기지 않아야 한다")
        void commits_transition_without_notification(final Transition transition) {
            // Given
            persistReservation(MachineAvailability.RESERVED,
                    builder -> builder.status(ReservationStatus.RESERVED).reservedAt(DateTimeUtil.nowInKorea()));
            final Long missingUserId = user.getId() + 1_000;
            final var transactionTemplate = new TransactionTemplate(transactionManager);

            // When & Then
            assertThatCode(() -> transactionTemplate.executeWithoutResult(status -> {
                final var lockedMachine = machineRepository.findByIdForUpdate(machine.getId()).orElseThrow();
                lockedMachine.markAsInUse();
                final var uninitializableUser = userRepository.getReferenceById(missingUserId);
                send(transition, uninitializableUser, lockedMachine);
            })).doesNotThrowAnyException();
            assertThat(machineRepository.findById(machine.getId())).map(Machine::getAvailability)
                    .contains(MachineAvailability.IN_USE);
            assertThat(notificationRepository.count()).isZero();
            then(fcmNotificationSupport).should(never()).send(any(User.class), anyString(), anyString());
        }

        private void send(final Transition transition, final User recipient, final Machine target) {
            switch (transition) {
                case START -> reservationNotificationSupport
                        .sendStarted(recipient, target, DateTimeUtil.nowInKorea().plusMinutes(30));
                case COMPLETION -> reservationNotificationSupport.sendCompletion(recipient, target);
                case INTERRUPTION -> reservationNotificationSupport.sendInterruption(recipient, target);
                case PAUSE_TIMEOUT -> reservationNotificationSupport.sendPauseTimeout(recipient, target);
            }
        }
    }

    private Long givenReservationReadyFor(final Transition transition) {
        final var now = DateTimeUtil.nowInKorea();
        return switch (transition) {
            case START -> {
                given(reservationStartDecisionSupport.isStarted(any(), anyBoolean())).willReturn(true);
                yield persistReservation(MachineAvailability.RESERVED,
                        builder -> builder.status(ReservationStatus.RESERVED).reservedAt(now));
            }
            case COMPLETION -> {
                given(completionDecisionSupport.decide(any(), any(), anyBoolean()))
                        .willReturn(CompletionDecision.completed(now, "job_finished"));
                final Long reservationId = persistRunningReservation(UnaryOperator.identity());
                given(machineShutdownClaimSupport.claimLockedMachine(any(Machine.class))).willReturn(
                        Optional.of(new MachineShutdownClaimSupport.ShutdownClaim(machine.getId(), "claim-token")));
                yield reservationId;
            }
            case INTERRUPTION -> {
                given(completionDecisionSupport.decide(any(), any(), anyBoolean()))
                        .willReturn(CompletionDecision.notCompleted());
                given(machineStateDetectionSupport.isInterrupted(any(), anyBoolean())).willReturn(true);
                yield persistRunningReservation(
                        builder -> builder.interruptionCount(ReservationConstants.INTERRUPTION_CONFIRM_THRESHOLD - 1));
            }
            case PAUSE_TIMEOUT -> {
                given(completionDecisionSupport.decide(any(), any(), anyBoolean()))
                        .willReturn(CompletionDecision.notCompleted());
                given(machineStateDetectionSupport.isPaused(any(), anyBoolean())).willReturn(true);
                yield persistRunningReservation(
                        builder -> builder.pausedAt(now.minusMinutes(ReservationConstants.PAUSE_TIMEOUT_MINUTES + 1)));
            }
        };
    }

    private Long persistRunningReservation(final UnaryOperator<Reservation.ReservationBuilder> customizer) {
        final var now = DateTimeUtil.nowInKorea();
        return persistReservation(MachineAvailability.IN_USE,
                builder -> customizer.apply(builder.status(ReservationStatus.RUNNING).reservedAt(now.minusMinutes(10))
                        .startTime(now.minusMinutes(5))));
    }

    private Long persistReservation(final MachineAvailability availability,
            final UnaryOperator<Reservation.ReservationBuilder> customizer) {
        machine = machineRepository.save(Machine.builder().name("W3L1").type(MachineType.WASHER).deviceId("device-w3l1")
                .floor(3).position(Position.LEFT).number(1).availability(availability).build());
        return reservationRepository.save(customizer.apply(Reservation.builder().user(user).machine(machine)).build())
                .getId();
    }

    private void process(final Transition transition, final Long reservationId) {
        if (transition == Transition.START) {
            reservationLifecycleProcessor.processReservedToRunning(reservationId, deviceStatus());
            return;
        }
        reservationLifecycleProcessor.processRunningToCompleted(reservationId, deviceStatus());
    }

    private SmartThingsDeviceStatusResDto deviceStatus() {
        final var machineState = new SmartThingsDeviceStatusResDto.AttributeState("run", "2026-10-12T00:00:00Z", null);
        final var jobState = new SmartThingsDeviceStatusResDto.AttributeState("wash", "2026-10-12T00:00:00Z", null);
        final var washerState = new SmartThingsDeviceStatusResDto.WasherOperatingState(machineState, jobState, null);
        final var component = new SmartThingsDeviceStatusResDto.ComponentStatus(washerState, null, null, null);
        return new SmartThingsDeviceStatusResDto(Map.of("main", component));
    }

    private void assertTransitionCommitted(final Transition transition, final Long reservationId) {
        assertThat(reservationRepository.findById(reservationId)).map(Reservation::getStatus)
                .contains(transition.reservationStatus);
        assertThat(machineRepository.findById(machine.getId())).map(Machine::getAvailability)
                .contains(transition.machineAvailability);
    }

    private void blockNotificationInsert() {
        jdbcTemplate
                .execute("ALTER TABLE notifications ADD CONSTRAINT " + INSERT_BLOCKING_CONSTRAINT + " CHECK (id < 0)");
    }

    private void unblockNotificationInsert() {
        jdbcTemplate.execute("ALTER TABLE notifications DROP CONSTRAINT " + INSERT_BLOCKING_CONSTRAINT);
    }

    /**
     * 알림을 동반하는 lifecycle 전이와 전이 후 기대 상태.
     */
    private enum Transition {
        START(ReservationStatus.RUNNING, MachineAvailability.IN_USE, NotificationType.STARTED), COMPLETION(
                ReservationStatus.COMPLETED, MachineAvailability.AVAILABLE,
                NotificationType.COMPLETION), INTERRUPTION(ReservationStatus.CANCELLED, MachineAvailability.AVAILABLE,
                        NotificationType.INTERRUPTION), PAUSE_TIMEOUT(ReservationStatus.CANCELLED,
                                MachineAvailability.AVAILABLE, NotificationType.PAUSE_TIMEOUT);

        private final ReservationStatus reservationStatus;
        private final MachineAvailability machineAvailability;
        private final NotificationType notificationType;

        Transition(final ReservationStatus reservationStatus,
                final MachineAvailability machineAvailability,
                final NotificationType notificationType) {
            this.reservationStatus = reservationStatus;
            this.machineAvailability = machineAvailability;
            this.notificationType = notificationType;
        }
    }
}
