package team.washer.server.v2.domain.machine.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import team.washer.server.v2.domain.machine.dto.response.ForceStopMachineResDto;
import team.washer.server.v2.domain.machine.entity.Machine;
import team.washer.server.v2.domain.machine.enums.MachineAvailability;
import team.washer.server.v2.domain.machine.enums.MachineStatus;
import team.washer.server.v2.domain.machine.enums.MachineType;
import team.washer.server.v2.domain.machine.enums.Position;
import team.washer.server.v2.domain.machine.repository.MachineRepository;
import team.washer.server.v2.domain.machine.service.impl.ForceStopMachineServiceImpl;
import team.washer.server.v2.domain.notification.enums.NotificationType;
import team.washer.server.v2.domain.notification.repository.NotificationRepository;
import team.washer.server.v2.domain.notification.support.FcmNotificationSupport;
import team.washer.server.v2.domain.notification.support.ReservationNotificationSupport;
import team.washer.server.v2.domain.reservation.entity.Reservation;
import team.washer.server.v2.domain.reservation.enums.ReservationStatus;
import team.washer.server.v2.domain.reservation.repository.ReservationRepository;
import team.washer.server.v2.domain.smartthings.dto.response.SmartThingsDeviceStatusResDto;
import team.washer.server.v2.domain.smartthings.service.SendDeviceCommandService;
import team.washer.server.v2.domain.smartthings.support.DeviceStatusQuerySupport;
import team.washer.server.v2.domain.smartthings.support.MachineShutdownClaimSupport;
import team.washer.server.v2.domain.user.entity.User;
import team.washer.server.v2.domain.user.repository.UserRepository;
import team.washer.server.v2.global.common.transaction.AfterCommitExecutor;
import team.washer.server.v2.global.config.JpaAuditingConfig;
import team.washer.server.v2.global.config.QueryDslConfig;
import team.washer.server.v2.global.util.DateTimeUtil;

/**
 * 관리자 강제 정지의 예약 취소와 알림 저장의 트랜잭션 경계를 실제 JPA 트랜잭션으로 검증합니다.
 *
 * <p>
 * 알림 저장 실패는 알림 테이블에 모든 삽입을 거부하는 제약을 걸어 재현합니다. 정지 명령을 보낸 뒤 알림 저장이 실패해도 예약 취소와 기기
 * 상태 변경이 커밋되고, 전원 차단 claim이 해제되는지 확인합니다.
 */
@DataJpaTest
@Import({QueryDslConfig.class, JpaAuditingConfig.class, ForceStopMachineServiceImpl.class,
        MachineShutdownClaimSupport.class, ReservationNotificationSupport.class, AfterCommitExecutor.class})
@TestPropertySource(properties = {"spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.docker.compose.enabled=false"})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DisplayName("관리자 강제 정지와 알림 저장 트랜잭션 분리 통합 테스트")
class ForceStopNotificationIsolationTest {

    private static final String INSERT_BLOCKING_CONSTRAINT = "notification_insert_blocked";

    @Autowired
    private ForceStopMachineServiceImpl forceStopMachineService;

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

    @MockitoBean
    private DeviceStatusQuerySupport deviceStatusQuerySupport;

    @MockitoBean
    private SendDeviceCommandService sendDeviceCommandService;

    @MockitoBean
    private FcmNotificationSupport fcmNotificationSupport;

    private User user;
    private Machine machine;
    private Long reservationId;

    @BeforeEach
    void setUp() {
        final var newUser = User.builder().name("김세탁").studentId("2101").roomNumber("301").grade(1).floor(3).build();
        newUser.updateFcmToken("fcm-token");
        user = userRepository.save(newUser);
        machine = machineRepository.save(Machine.builder().name("W3L1").type(MachineType.WASHER).deviceId("device-w3l1")
                .floor(3).position(Position.LEFT).number(1).status(MachineStatus.NORMAL)
                .availability(MachineAvailability.IN_USE).build());
        final var now = DateTimeUtil.nowInKorea();
        reservationId = reservationRepository
                .save(Reservation.builder().user(user).machine(machine).status(ReservationStatus.RUNNING)
                        .reservedAt(now.minusMinutes(10)).startTime(now.minusMinutes(5)).build())
                .getId();
        given(deviceStatusQuerySupport.queryDeviceStatus("device-w3l1")).willReturn(runningWasherStatus());
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

        @Test
        @DisplayName("예약 취소와 기기 상태를 커밋하고 강제 정지 알림을 한 번 저장·전송해야 한다")
        void commits_cancellation_and_persists_notification_once() {
            // When
            final var result = forceStopMachineService.execute(machine.getId());

            // Then
            assertCancellationCommitted(result);
            assertThat(notificationRepository.findByUserAndType(user, NotificationType.FORCE_STOPPED)).hasSize(1);
            then(fcmNotificationSupport).should(times(1)).send(any(User.class), anyString(), anyString());
        }
    }

    @Nested
    @DisplayName("알림 저장이 실패하면")
    class Context_with_notification_persist_failure {

        @Test
        @DisplayName("예외 없이 예약 취소와 기기 상태를 커밋하고 claim을 해제하며 알림과 FCM은 남기지 않아야 한다")
        void commits_cancellation_without_notification() {
            // Given
            blockNotificationInsert();

            // When
            final var result = forceStopMachineService.execute(machine.getId());

            // Then
            assertCancellationCommitted(result);
            assertThat(notificationRepository.count()).isZero();
            then(fcmNotificationSupport).should(never()).send(any(User.class), anyString(), anyString());
        }
    }

    private void assertCancellationCommitted(final ForceStopMachineResDto result) {
        assertThat(result.cancelledReservationId()).isEqualTo(reservationId);
        assertThat(reservationRepository.findById(reservationId)).map(Reservation::getStatus)
                .contains(ReservationStatus.CANCELLED);
        final var committedMachine = machineRepository.findById(machine.getId()).orElseThrow();
        assertThat(committedMachine.getAvailability()).isEqualTo(MachineAvailability.AVAILABLE);
        assertThat(committedMachine.hasActiveShutdownClaim()).isFalse();
        then(sendDeviceCommandService).should(times(1)).execute(eq("device-w3l1"), any());
    }

    private void blockNotificationInsert() {
        jdbcTemplate
                .execute("ALTER TABLE notifications ADD CONSTRAINT " + INSERT_BLOCKING_CONSTRAINT + " CHECK (id < 0)");
    }

    private SmartThingsDeviceStatusResDto runningWasherStatus() {
        final var machineState = new SmartThingsDeviceStatusResDto.AttributeState("run", "2026-10-12T00:00:00Z", null);
        final var jobState = new SmartThingsDeviceStatusResDto.AttributeState("wash", "2026-10-12T00:00:00Z", null);
        final var washerState = new SmartThingsDeviceStatusResDto.WasherOperatingState(machineState, jobState, null);
        final var component = new SmartThingsDeviceStatusResDto.ComponentStatus(washerState, null, null, null);
        return new SmartThingsDeviceStatusResDto(Map.of("main", component));
    }
}
