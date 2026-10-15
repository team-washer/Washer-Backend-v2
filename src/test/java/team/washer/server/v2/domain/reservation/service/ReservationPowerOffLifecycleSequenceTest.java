package team.washer.server.v2.domain.reservation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import team.washer.server.v2.domain.machine.entity.Machine;
import team.washer.server.v2.domain.machine.enums.MachineAvailability;
import team.washer.server.v2.domain.machine.enums.MachineType;
import team.washer.server.v2.domain.machine.repository.MachineRepository;
import team.washer.server.v2.domain.notification.support.ReservationNotificationSupport;
import team.washer.server.v2.domain.reservation.entity.Reservation;
import team.washer.server.v2.domain.reservation.enums.ReservationStatus;
import team.washer.server.v2.domain.reservation.repository.ReservationRepository;
import team.washer.server.v2.domain.reservation.service.impl.ReservationLifecycleProcessor;
import team.washer.server.v2.domain.reservation.support.ReservationCompletionDecisionSupport;
import team.washer.server.v2.domain.reservation.support.ReservationStartDecisionSupport;
import team.washer.server.v2.domain.smartthings.dto.response.SmartThingsDeviceStatusResDto;
import team.washer.server.v2.domain.smartthings.dto.response.SmartThingsDeviceStatusResDto.AttributeState;
import team.washer.server.v2.domain.smartthings.dto.response.SmartThingsDeviceStatusResDto.ComponentStatus;
import team.washer.server.v2.domain.smartthings.dto.response.SmartThingsDeviceStatusResDto.DryerOperatingState;
import team.washer.server.v2.domain.smartthings.dto.response.SmartThingsDeviceStatusResDto.SwitchCapability;
import team.washer.server.v2.domain.smartthings.dto.response.SmartThingsDeviceStatusResDto.WasherOperatingState;
import team.washer.server.v2.domain.smartthings.support.MachineShutdownClaimSupport;
import team.washer.server.v2.domain.smartthings.support.MachineStateDetectionSupport;
import team.washer.server.v2.domain.user.entity.User;
import team.washer.server.v2.domain.user.repository.UserRepository;
import team.washer.server.v2.global.common.constants.ReservationConstants;
import team.washer.server.v2.global.util.DateTimeUtil;

/**
 * 전원이 꺼진 정지가 관측되는 상태 전이를 실제 판정 컴포넌트로 이어 붙여 검증한다. 판정 컴포넌트를 목으로 대체하는
 * {@code ReservationLifecycleProcessorTest}와 달리, 폴링 순서에 따른 최종 예약 상태·기기 점유·알림
 * 종류를 한 흐름으로 확인한다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("전원 꺼짐 상태 전이에 따른 예약 lifecycle 처리")
class ReservationPowerOffLifecycleSequenceTest {

    private static final Long RESERVATION_ID = 1L;
    private static final Long MACHINE_ID = 2L;
    private static final Long USER_ID = 3L;
    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private MachineRepository machineRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ReservationNotificationSupport reservationNotificationSupport;

    private ReservationLifecycleProcessor reservationLifecycleProcessor;

    private User user;
    private LocalDateTime now;

    @BeforeEach
    void setUp() {
        var machineStateDetectionSupport = new MachineStateDetectionSupport();
        reservationLifecycleProcessor = new ReservationLifecycleProcessor(reservationRepository,
                machineRepository,
                machineStateDetectionSupport,
                new ReservationCompletionDecisionSupport(machineStateDetectionSupport),
                mock(ReservationStartDecisionSupport.class),
                reservationNotificationSupport,
                new MachineShutdownClaimSupport(machineRepository, reservationRepository),
                userRepository);
        user = User.builder().name("김철수").studentId("20210001").roomNumber("301").grade(3).floor(3).build();
        now = DateTimeUtil.nowInKorea();
    }

    private Machine inUseMachine(MachineType machineType) {
        return Machine.builder().name(machineType == MachineType.WASHER ? "WASHER-3F-L1" : "DRYER-3F-R1")
                .type(machineType).deviceId("device-1").floor(3).availability(MachineAvailability.IN_USE).build();
    }

    private Reservation givenRunningReservation(Machine machine,
            LocalDateTime startTime,
            LocalDateTime expectedCompletionTime) {
        var reservation = Reservation.builder().user(user).machine(machine).reservedAt(startTime.minusMinutes(1))
                .startTime(startTime).expectedCompletionTime(expectedCompletionTime).status(ReservationStatus.RUNNING)
                .build();
        when(reservationRepository.findUserIdById(RESERVATION_ID)).thenReturn(Optional.of(USER_ID));
        when(reservationRepository.findMachineIdById(RESERVATION_ID)).thenReturn(Optional.of(MACHINE_ID));
        when(userRepository.findByIdForUpdate(USER_ID)).thenReturn(Optional.of(user));
        when(machineRepository.findByIdForUpdate(MACHINE_ID)).thenReturn(Optional.of(machine));
        when(reservationRepository.findByIdForUpdateWithoutRelations(RESERVATION_ID))
                .thenReturn(Optional.of(reservation));
        return reservation;
    }

    private static AttributeState attr(String value, LocalDateTime koreaTime) {
        var timestamp = koreaTime == null
                ? null
                : koreaTime.atZone(KOREA_ZONE).withZoneSameInstant(ZoneId.of("UTC")).toLocalDateTime() + "Z";
        return new AttributeState(value, timestamp, null);
    }

    private static SmartThingsDeviceStatusResDto status(MachineType machineType,
            String switchState,
            String machineState,
            String jobState,
            LocalDateTime updatedAt) {
        return status(machineType, switchState, machineState, jobState, updatedAt, null);
    }

    private static SmartThingsDeviceStatusResDto status(MachineType machineType,
            String switchState,
            String machineState,
            String jobState,
            LocalDateTime updatedAt,
            AttributeState completionTime) {
        var switchCapability = new SwitchCapability(attr(switchState, updatedAt));
        var component = machineType == MachineType.WASHER
                ? new ComponentStatus(new WasherOperatingState(attr(machineState, updatedAt),
                        attr(jobState, updatedAt),
                        completionTime), null, switchCapability, null)
                : new ComponentStatus(null,
                        new DryerOperatingState(attr(machineState, updatedAt),
                                attr(jobState, updatedAt),
                                completionTime),
                        switchCapability,
                        null);
        return new SmartThingsDeviceStatusResDto(Map.of("main", component));
    }

    private static SmartThingsDeviceStatusResDto poweredOffIdle(MachineType machineType, LocalDateTime stoppedAt) {
        return status(machineType, "off", "stop", "none", stoppedAt);
    }

    private static SmartThingsDeviceStatusResDto poweredOffIdle(MachineType machineType,
            LocalDateTime poweredOffAt,
            LocalDateTime reportedCompletionTime) {
        var completionTime = attr(
                reportedCompletionTime.atZone(KOREA_ZONE).withZoneSameInstant(ZoneId.of("UTC")).toLocalDateTime() + "Z",
                poweredOffAt);
        return status(machineType, "off", "stop", "none", poweredOffAt, completionTime);
    }

    private void poll(SmartThingsDeviceStatusResDto status, int times) {
        for (var i = 0; i < times; i++) {
            reservationLifecycleProcessor.processRunningToCompleted(RESERVATION_ID, status);
        }
    }

    private void assertCompletedOnce(Reservation reservation, Machine machine) {
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.COMPLETED);
        assertThat(reservation.getActualCompletionTime()).isNotNull();
        assertThat(reservation.getCancelledAt()).isNull();
        assertThat(reservation.getInterruptionCount()).isZero();
        assertThat(machine.getAvailability()).isEqualTo(MachineAvailability.AVAILABLE);
        verify(reservationNotificationSupport, times(1)).sendCompletion(reservation.getId(), user, machine);
        verify(reservationNotificationSupport, never()).sendInterruption(any(), any(), any());
    }

    private void assertCancelledOnce(Reservation reservation, Machine machine) {
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(reservation.getCancelledAt()).isNotNull();
        assertThat(reservation.getActualCompletionTime()).isNull();
        assertThat(machine.getAvailability()).isEqualTo(MachineAvailability.AVAILABLE);
        verify(reservationNotificationSupport, times(1)).sendInterruption(reservation.getId(), user, machine);
        verify(reservationNotificationSupport, never()).sendCompletion(any(), any(), any());
    }

    /**
     * 완료 신호를 폴링 사이에 놓친 정상 종료와 종료 직전의 조기 전원 차단은 같은 상태로 보고된다. 둘을 구분할 운영 근거가 확보되기 전까지는
     * 완료로 확정하지 않고 기존 중단 정책을 그대로 따른다.
     */
    @Nested
    @DisplayName("완료 예정 시각 무렵에 멈춘 뒤 전원이 꺼지면")
    class PoweredOffNearExpectedCompletion {

        @ParameterizedTest
        @EnumSource(MachineType.class)
        @DisplayName("한 번의 관측만으로 완료를 확정하지 않고 확정 임계치 전까지 RUNNING을 유지한다")
        void shouldNotComplete_BeforeThreshold(MachineType machineType) {
            // Given
            var machine = inUseMachine(machineType);
            var reservation = givenRunningReservation(machine, now.minusMinutes(50), now.minusMinutes(2));
            poll(status(machineType, "on", "run", machineType == MachineType.WASHER ? "spin" : "cooling", null), 1);

            // When
            var result = reservationLifecycleProcessor.processRunningToCompleted(RESERVATION_ID,
                    poweredOffIdle(machineType, now.minusMinutes(1)));
            poll(poweredOffIdle(machineType, now.minusMinutes(1)),
                    ReservationConstants.INTERRUPTION_CONFIRM_THRESHOLD - 2);

            // Then
            assertThat(result).isEmpty();
            assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.RUNNING);
            assertThat(reservation.getInterruptionCount())
                    .isEqualTo(ReservationConstants.INTERRUPTION_CONFIRM_THRESHOLD - 1);
            assertThat(machine.getAvailability()).isEqualTo(MachineAvailability.IN_USE);
            verify(reservationNotificationSupport, never()).sendCompletion(any(), any(), any());
            verify(reservationNotificationSupport, never()).sendInterruption(any(), any(), any());
        }

        @ParameterizedTest
        @EnumSource(MachineType.class)
        @DisplayName("연속 임계치만큼 관측되면 기존 정책대로 CANCELLED로 전환하고 중단 알림만 한 번 전송한다")
        void shouldCancel_AfterThreshold(MachineType machineType) {
            // Given
            var machine = inUseMachine(machineType);
            var reservation = givenRunningReservation(machine, now.minusMinutes(50), now.minusMinutes(2));

            // When
            poll(poweredOffIdle(machineType, now.minusMinutes(1)),
                    ReservationConstants.INTERRUPTION_CONFIRM_THRESHOLD + 1);

            // Then
            assertCancelledOnce(reservation, machine);
        }
    }

    @Nested
    @DisplayName("완료 신호를 직접 관측한 정상 종료는")
    class ObservedCompletionSignal {

        @ParameterizedTest
        @EnumSource(MachineType.class)
        @DisplayName("이후 전원이 꺼진 상태가 관측되어도 COMPLETED를 유지하고 중단 알림을 보내지 않는다")
        void shouldStayCompleted_WhenPoweredOffAfterFinish(MachineType machineType) {
            // Given
            var machine = inUseMachine(machineType);
            var reservation = givenRunningReservation(machine, now.minusMinutes(50), now.plusMinutes(20));
            var finishedJobState = machineType == MachineType.WASHER ? "finish" : "finished";
            poll(status(machineType, "on", "stop", finishedJobState, now.minusMinutes(1)), 1);

            // When
            poll(poweredOffIdle(machineType, now.minusMinutes(1)), ReservationConstants.INTERRUPTION_CONFIRM_THRESHOLD);

            // Then
            assertCompletedOnce(reservation, machine);
        }
    }

    @Nested
    @DisplayName("사이클 도중 전원이 꺼지면")
    class PoweredOffMidCycle {

        @Test
        @DisplayName("확정 임계치 전까지는 RUNNING을 유지하며 알림을 보내지 않는다")
        void shouldKeepRunning_BeforeThreshold() {
            // Given
            var machine = inUseMachine(MachineType.WASHER);
            var reservation = givenRunningReservation(machine, now.minusMinutes(20), now.plusMinutes(30));

            // When
            poll(poweredOffIdle(MachineType.WASHER, now.minusMinutes(1)),
                    ReservationConstants.INTERRUPTION_CONFIRM_THRESHOLD - 1);

            // Then
            assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.RUNNING);
            assertThat(reservation.getInterruptionCount())
                    .isEqualTo(ReservationConstants.INTERRUPTION_CONFIRM_THRESHOLD - 1);
            assertThat(machine.getAvailability()).isEqualTo(MachineAvailability.IN_USE);
            verify(reservationNotificationSupport, never()).sendInterruption(any(), any(), any());
            verify(reservationNotificationSupport, never()).sendCompletion(any(), any(), any());
        }

        @ParameterizedTest
        @EnumSource(MachineType.class)
        @DisplayName("완료 예정 시각보다 훨씬 일찍 멈춘 상태가 연속 임계치만큼 관측되면 패널티 없이 CANCELLED로 전환하고 중단 알림만 한 번 전송한다")
        void shouldCancel_WhenPoweredOffLongBeforeExpectedCompletion(MachineType machineType) {
            // Given
            var machine = inUseMachine(machineType);
            var reservation = givenRunningReservation(machine, now.minusMinutes(20), now.plusMinutes(30));

            // When
            poll(poweredOffIdle(machineType, now.minusMinutes(1)),
                    ReservationConstants.INTERRUPTION_CONFIRM_THRESHOLD + 1);

            // Then
            assertCancelledOnce(reservation, machine);
        }

        @Test
        @DisplayName("임계치 전에 다시 작동이 관측되면 중단 후보 카운트를 초기화하고 RUNNING을 유지한다")
        void shouldResetCount_WhenRunningObservedAgain() {
            // Given
            var machine = inUseMachine(MachineType.WASHER);
            var reservation = givenRunningReservation(machine, now.minusMinutes(20), now.plusMinutes(30));
            poll(poweredOffIdle(MachineType.WASHER, now.minusMinutes(1)),
                    ReservationConstants.INTERRUPTION_CONFIRM_THRESHOLD - 1);

            // When
            poll(status(MachineType.WASHER, "on", "run", "rinse", now), 1);

            // Then
            assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.RUNNING);
            assertThat(reservation.getInterruptionCount()).isZero();
            verify(reservationNotificationSupport, never()).sendInterruption(any(), any(), any());
        }
    }

    @Nested
    @DisplayName("일시정지된 채로 전원이 꺼지면")
    class PoweredOffWhilePaused {

        @ParameterizedTest
        @EnumSource(MachineType.class)
        @DisplayName("조회가 끊겨 일시정지가 완료 예정 무렵에야 관측됐어도 완료로 확정하지 않고 CANCELLED로 전환하며 중단 알림만 한 번 전송한다")
        void shouldCancel_WhenPauseObservedLate(MachineType machineType) {
            // Given
            var machine = inUseMachine(machineType);
            var reservation = givenRunningReservation(machine, now.minusMinutes(50), now.plusMinutes(2));
            var devicePausedAt = now.minusMinutes(16);
            poll(status(machineType, "on", "pause", "rinse", devicePausedAt), 1);
            assertThat(reservation.getPausedAt()).isAfter(devicePausedAt);

            // When
            poll(poweredOffIdle(machineType, now.minusMinutes(1)),
                    ReservationConstants.INTERRUPTION_CONFIRM_THRESHOLD + 1);

            // Then
            assertCancelledOnce(reservation, machine);
        }
    }

    /**
     * 삼성 세탁기·건조기는 전원이 꺼질 때 completionTime을 "꺼진 시각 + 기본 코스 시간"으로 다시 보고한다. 이 값은 방금 끝난
     * 사이클의 완료 예정 시각이 아니므로 예약에 저장되면 안 된다.
     */
    @Nested
    @DisplayName("전원이 꺼지면서 기기가 완료 예정 시각을 기본 코스 시간으로 다시 보고해도")
    class CompletionTimeResetOnPowerOff {

        @ParameterizedTest
        @EnumSource(MachineType.class)
        @DisplayName("완료 예정 무렵에 꺼진 예약은 저장된 완료 예정 시각을 유지한 채 CANCELLED로 전환한다")
        void shouldKeepStoredExpectedCompletionTime_WhenPoweredOffNearExpectedCompletion(MachineType machineType) {
            // Given
            var machine = inUseMachine(machineType);
            var expectedCompletionTime = now.minusMinutes(2);
            var reservation = givenRunningReservation(machine, now.minusMinutes(50), expectedCompletionTime);
            var poweredOffAt = now.minusMinutes(1);

            // When
            poll(poweredOffIdle(machineType, poweredOffAt, poweredOffAt.plusMinutes(45)),
                    ReservationConstants.INTERRUPTION_CONFIRM_THRESHOLD);

            // Then
            assertCancelledOnce(reservation, machine);
            assertThat(reservation.getExpectedCompletionTime()).isEqualTo(expectedCompletionTime);
        }

        @ParameterizedTest
        @EnumSource(MachineType.class)
        @DisplayName("사이클 도중 꺼진 예약은 저장된 완료 예정 시각을 유지한 채 CANCELLED로 전환한다")
        void shouldCancel_WithoutOverwritingExpectedCompletionTime(MachineType machineType) {
            // Given
            var machine = inUseMachine(machineType);
            var expectedCompletionTime = now.plusMinutes(30);
            var reservation = givenRunningReservation(machine, now.minusMinutes(20), expectedCompletionTime);
            var poweredOffAt = now.minusMinutes(1);

            // When
            poll(poweredOffIdle(machineType, poweredOffAt, poweredOffAt.plusMinutes(45)),
                    ReservationConstants.INTERRUPTION_CONFIRM_THRESHOLD);

            // Then
            assertCancelledOnce(reservation, machine);
            assertThat(reservation.getExpectedCompletionTime()).isEqualTo(expectedCompletionTime);
        }
    }

    @Nested
    @DisplayName("완료 근거를 대조할 수 없는 전원 꺼짐은")
    class PoweredOffWithoutEvidence {

        @Test
        @DisplayName("상태 갱신 시각이 없으면 연속 임계치 관측 후 CANCELLED로 전환한다")
        void shouldCancel_WhenTimestampMissing() {
            // Given
            var machine = inUseMachine(MachineType.WASHER);
            var reservation = givenRunningReservation(machine, now.minusMinutes(50), now.minusMinutes(2));

            // When
            poll(poweredOffIdle(MachineType.WASHER, null), ReservationConstants.INTERRUPTION_CONFIRM_THRESHOLD);

            // Then
            assertCancelledOnce(reservation, machine);
        }

        @Test
        @DisplayName("상태 갱신 시각이 예약 시작 전이면 연속 임계치 관측 후 CANCELLED로 전환한다")
        void shouldCancel_WhenTimestampBeforeStart() {
            // Given
            var machine = inUseMachine(MachineType.WASHER);
            var reservation = givenRunningReservation(machine, now.minusMinutes(3), now.plusMinutes(1));

            // When
            poll(poweredOffIdle(MachineType.WASHER, now.minusMinutes(10)),
                    ReservationConstants.INTERRUPTION_CONFIRM_THRESHOLD);

            // Then
            assertCancelledOnce(reservation, machine);
        }

        @Test
        @DisplayName("예약에 완료 예정 시각이 없으면 연속 임계치 관측 후 CANCELLED로 전환한다")
        void shouldCancel_WhenExpectedCompletionTimeMissing() {
            // Given
            var machine = inUseMachine(MachineType.WASHER);
            var reservation = givenRunningReservation(machine, now.minusMinutes(50), null);

            // When
            poll(poweredOffIdle(MachineType.WASHER, now.minusMinutes(1)),
                    ReservationConstants.INTERRUPTION_CONFIRM_THRESHOLD);

            // Then
            assertCancelledOnce(reservation, machine);
        }
    }

    @Nested
    @DisplayName("machineState를 읽을 수 없으면")
    class UnknownOperatingState {

        @Test
        @DisplayName("전원이 켜져 있는 한 RUNNING을 유지하고 완료·중단 어느 쪽으로도 확정하지 않는다")
        void shouldKeepRunning_WhenOperatingStateUnknown() {
            // Given
            var machine = inUseMachine(MachineType.WASHER);
            var reservation = givenRunningReservation(machine, now.minusMinutes(50), now.minusMinutes(2));

            // When
            poll(status(MachineType.WASHER, "on", null, null, null),
                    ReservationConstants.INTERRUPTION_CONFIRM_THRESHOLD + 1);

            // Then
            assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.RUNNING);
            assertThat(reservation.getInterruptionCount()).isZero();
            assertThat(machine.getAvailability()).isEqualTo(MachineAvailability.IN_USE);
            verify(reservationNotificationSupport, never()).sendInterruption(any(), any(), any());
            verify(reservationNotificationSupport, never()).sendCompletion(any(), any(), any());
        }
    }
}
