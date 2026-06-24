package team.washer.server.v2.domain.reservation.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import team.washer.server.v2.domain.machine.entity.Machine;
import team.washer.server.v2.domain.machine.repository.MachineRepository;
import team.washer.server.v2.domain.notification.support.ReservationNotificationSupport;
import team.washer.server.v2.domain.reservation.entity.Reservation;
import team.washer.server.v2.domain.reservation.repository.ReservationRepository;
import team.washer.server.v2.domain.reservation.service.impl.ReservationLifecycleProcessor;
import team.washer.server.v2.domain.smartthings.dto.response.SmartThingsDeviceStatusResDto;
import team.washer.server.v2.domain.smartthings.support.MachineStateDetectionSupport;
import team.washer.server.v2.domain.user.entity.User;
import team.washer.server.v2.global.common.constants.ReservationConstants;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReservationLifecycleProcessor 개별 예약 처리")
class ReservationLifecycleProcessorTest {

    private static final Long RESERVATION_ID = 1L;

    @InjectMocks
    private ReservationLifecycleProcessor reservationLifecycleProcessor;

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private MachineRepository machineRepository;

    @Mock
    private MachineStateDetectionSupport machineStateDetectionSupport;

    @Mock
    private ReservationNotificationSupport reservationNotificationSupport;

    @Mock
    private Reservation reservation;

    @Mock
    private Machine machine;

    @Mock
    private User user;

    private SmartThingsDeviceStatusResDto buildDeviceStatus(String completionTime) {
        var completionTimeAttr = new SmartThingsDeviceStatusResDto.AttributeState(completionTime, null, null);
        var washerOpState = new SmartThingsDeviceStatusResDto.WasherOperatingState(null, null, completionTimeAttr);
        var componentStatus = new SmartThingsDeviceStatusResDto.ComponentStatus(washerOpState, null, null, null);
        return new SmartThingsDeviceStatusResDto(Map.of("main", componentStatus));
    }

    private SmartThingsDeviceStatusResDto buildWasherStatusWithTimestamp(String machineStateTimestamp,
            String jobStateTimestamp) {
        var machineStateAttr = new SmartThingsDeviceStatusResDto.AttributeState("stop", machineStateTimestamp, null);
        var jobStateAttr = new SmartThingsDeviceStatusResDto.AttributeState("finish", jobStateTimestamp, null);
        var washerOpState = new SmartThingsDeviceStatusResDto.WasherOperatingState(machineStateAttr,
                jobStateAttr,
                null);
        var componentStatus = new SmartThingsDeviceStatusResDto.ComponentStatus(washerOpState, null, null, null);
        return new SmartThingsDeviceStatusResDto(Map.of("main", componentStatus));
    }

    private String isoUtc(LocalDateTime koreaTime) {
        return koreaTime.atZone(ZoneId.of("Asia/Seoul")).withZoneSameInstant(ZoneId.of("UTC")).toLocalDateTime()
                .toString() + "Z";
    }

    @Nested
    @DisplayName("RESERVED -> RUNNING 전환 처리")
    class ProcessReservedToRunningTest {

        @Test
        @DisplayName("RESERVED 상태이고 기기가 작동 중이면 RUNNING으로 전환하고 시작 알림을 전송한다")
        void shouldStartReservation_WhenReservedAndMachineRunning() {
            // Given
            var deviceStatus = buildDeviceStatus("2026-01-26T15:30:00Z");
            when(reservationRepository.findById(RESERVATION_ID)).thenReturn(Optional.of(reservation));
            when(reservation.isReserved()).thenReturn(true);
            when(reservation.getMachine()).thenReturn(machine);
            when(reservation.getUser()).thenReturn(user);
            when(machineStateDetectionSupport.isRunning(any(SmartThingsDeviceStatusResDto.class), anyBoolean()))
                    .thenReturn(true);

            // When
            reservationLifecycleProcessor.processReservedToRunning(RESERVATION_ID, deviceStatus);

            // Then
            verify(reservation, times(1)).start(any(LocalDateTime.class));
            verify(reservationRepository, times(1)).save(reservation);
            verify(reservationNotificationSupport, times(1)).sendStarted(any(), any(), any(LocalDateTime.class));
        }

        @Test
        @DisplayName("RESERVED 상태이지만 기기가 작동 중이지 않으면 전환하지 않는다")
        void shouldNotStartReservation_WhenReservedButMachineNotRunning() {
            // Given
            var deviceStatus = buildDeviceStatus(null);
            when(reservationRepository.findById(RESERVATION_ID)).thenReturn(Optional.of(reservation));
            when(reservation.isReserved()).thenReturn(true);
            when(reservation.getMachine()).thenReturn(machine);
            when(machineStateDetectionSupport.isRunning(any(SmartThingsDeviceStatusResDto.class), anyBoolean()))
                    .thenReturn(false);

            // When
            reservationLifecycleProcessor.processReservedToRunning(RESERVATION_ID, deviceStatus);

            // Then
            verify(reservation, never()).start(any());
            verify(reservationRepository, never()).save(reservation);
        }

        @Test
        @DisplayName("재조회 시점에 RESERVED 상태가 아니면 처리하지 않는다")
        void shouldSkip_WhenNoLongerReserved() {
            // Given
            var deviceStatus = buildDeviceStatus(null);
            when(reservationRepository.findById(RESERVATION_ID)).thenReturn(Optional.of(reservation));
            when(reservation.isReserved()).thenReturn(false);

            // When
            reservationLifecycleProcessor.processReservedToRunning(RESERVATION_ID, deviceStatus);

            // Then
            verify(reservation, never()).start(any());
            verify(reservationRepository, never()).save(reservation);
        }
    }

    @Nested
    @DisplayName("RUNNING -> COMPLETED 전환 처리")
    class ProcessRunningToCompletedTest {

        private void givenRunningReservation() {
            when(reservationRepository.findById(RESERVATION_ID)).thenReturn(Optional.of(reservation));
            when(reservation.isRunning()).thenReturn(true);
            when(reservation.getMachine()).thenReturn(machine);
        }

        @Test
        @DisplayName("RUNNING 상태이고 기기 작업이 완료되면 COMPLETED로 전환하고 알림을 전송한다")
        void shouldCompleteReservation_WhenRunningAndMachineCompleted() {
            // Given
            var deviceStatus = buildDeviceStatus("2026-01-26T15:30:00Z");
            givenRunningReservation();
            when(reservation.getUser()).thenReturn(user);
            when(machineStateDetectionSupport.isCompleted(any(SmartThingsDeviceStatusResDto.class), anyBoolean()))
                    .thenReturn(Optional.of(LocalDateTime.now()));

            // When
            reservationLifecycleProcessor.processRunningToCompleted(RESERVATION_ID, deviceStatus);

            // Then
            verify(reservation, times(1)).complete();
            verify(reservationRepository, times(1)).save(reservation);
            verify(reservationNotificationSupport, times(1)).sendCompletion(user, machine);
        }

        @Test
        @DisplayName("완료 시각이 현재 예약 시작 시각보다 이전이면 이전 상태로 보고 완료 처리하지 않는다")
        void shouldNotCompleteReservation_WhenCompletionTimeBeforeReservationStartTime() {
            // Given
            var deviceStatus = buildDeviceStatus("2026-01-26T15:30:00Z");
            var staleCompletionTime = LocalDateTime.now().minusMinutes(5);
            givenRunningReservation();
            when(reservation.getStartTime()).thenReturn(LocalDateTime.now());
            when(machineStateDetectionSupport.isCompleted(any(SmartThingsDeviceStatusResDto.class), anyBoolean()))
                    .thenReturn(Optional.of(staleCompletionTime));

            // When
            reservationLifecycleProcessor.processRunningToCompleted(RESERVATION_ID, deviceStatus);

            // Then
            verify(reservation, never()).complete();
            verify(machine, never()).markAsAvailable();
            verify(reservationRepository, never()).save(reservation);
            verify(machineRepository, never()).save(machine);
            verify(reservationNotificationSupport, never()).sendCompletion(any(), any());
        }

        @Test
        @DisplayName("완료 신호의 갱신 시각이 예약 시작 전이면 이전 상태로 보고 완료 처리하지 않는다")
        void shouldNotCompleteReservation_WhenCompletionSignalTimestampBeforeReservationStartTime() {
            // Given
            var startTime = LocalDateTime.now();
            var staleTimestamp = isoUtc(startTime.minusMinutes(1));
            var deviceStatus = buildWasherStatusWithTimestamp(staleTimestamp, staleTimestamp);
            givenRunningReservation();
            when(machine.isWasher()).thenReturn(true);
            when(reservation.getStartTime()).thenReturn(startTime);
            when(machineStateDetectionSupport.isCompleted(any(SmartThingsDeviceStatusResDto.class), anyBoolean()))
                    .thenReturn(Optional.of(startTime.plusMinutes(1)));

            // When
            reservationLifecycleProcessor.processRunningToCompleted(RESERVATION_ID, deviceStatus);

            // Then
            verify(reservation, never()).complete();
            verify(machine, never()).markAsAvailable();
            verify(reservationRepository, never()).save(reservation);
            verify(machineRepository, never()).save(machine);
            verify(reservationNotificationSupport, never()).sendCompletion(any(), any());
        }

        @Test
        @DisplayName("예상 완료 시간이 충분히 남아 있으면 완료 신호를 보류하고 예약을 유지한다")
        void shouldNotCompleteReservation_WhenCompletionDetectedTooEarly() {
            // Given
            var deviceStatus = buildDeviceStatus(null);
            givenRunningReservation();
            when(reservation.getExpectedCompletionTime()).thenReturn(LocalDateTime.now().plusMinutes(10));
            when(machineStateDetectionSupport.isCompleted(any(SmartThingsDeviceStatusResDto.class), anyBoolean()))
                    .thenReturn(Optional.of(LocalDateTime.now()));

            // When
            reservationLifecycleProcessor.processRunningToCompleted(RESERVATION_ID, deviceStatus);

            // Then
            verify(reservation, never()).complete();
            verify(machine, never()).markAsAvailable();
            verify(reservationRepository, never()).save(reservation);
            verify(machineRepository, never()).save(machine);
            verify(reservationNotificationSupport, never()).sendCompletion(any(), any());
        }

        @Test
        @DisplayName("RUNNING 상태이고 기기 작업이 완료되지 않으면 예상 완료 시각을 갱신한다")
        void shouldUpdateExpectedCompletionTime_WhenRunningAndMachineNotCompleted() {
            // Given
            var deviceStatus = buildDeviceStatus("2026-01-26T15:30:00Z");
            givenRunningReservation();
            when(machineStateDetectionSupport.isCompleted(any(SmartThingsDeviceStatusResDto.class), anyBoolean()))
                    .thenReturn(Optional.empty());
            when(machineStateDetectionSupport.isInterrupted(any(SmartThingsDeviceStatusResDto.class), anyBoolean()))
                    .thenReturn(false);
            when(machineStateDetectionSupport.isPaused(any(SmartThingsDeviceStatusResDto.class), anyBoolean()))
                    .thenReturn(false);
            when(reservation.getExpectedCompletionTime()).thenReturn(null);

            // When
            reservationLifecycleProcessor.processRunningToCompleted(RESERVATION_ID, deviceStatus);

            // Then
            verify(reservation, never()).complete();
            verify(reservation, never()).cancel();
            verify(reservation, times(1)).updateExpectedCompletionTime(any(LocalDateTime.class));
            verify(reservationRepository, times(1)).save(reservation);
            verify(reservationNotificationSupport, never()).sendCompletion(any(), any());
        }

        @Test
        @DisplayName("중단이 감지되어도 확정 임계치 미만이면 카운트만 증가시키고 취소하지 않는다")
        void shouldOnlyIncrementCount_WhenInterruptionBelowThreshold() {
            // Given
            var deviceStatus = buildDeviceStatus(null);
            givenRunningReservation();
            when(machineStateDetectionSupport.isCompleted(any(SmartThingsDeviceStatusResDto.class), anyBoolean()))
                    .thenReturn(Optional.empty());
            when(machineStateDetectionSupport.isInterrupted(any(SmartThingsDeviceStatusResDto.class), anyBoolean()))
                    .thenReturn(true);
            when(reservation.getInterruptionCount()).thenReturn(1);

            // When
            reservationLifecycleProcessor.processRunningToCompleted(RESERVATION_ID, deviceStatus);

            // Then
            verify(reservation, times(1)).incrementInterruptionCount();
            verify(reservation, never()).cancel();
            verify(reservation, never()).clearInterruptionCount();
            verify(reservationRepository, times(1)).save(reservation);
            verify(machineRepository, never()).save(machine);
            verify(reservationNotificationSupport, never()).sendInterruption(any(), any());
        }

        @Test
        @DisplayName("중단이 확정 임계치까지 연속 감지되면 패널티 없이 CANCELLED로 전환하고 중단 알림을 전송한다")
        void shouldCancelWithoutPenaltyAndNotify_WhenInterruptionConfirmed() {
            // Given
            var deviceStatus = buildDeviceStatus(null);
            givenRunningReservation();
            when(reservation.getUser()).thenReturn(user);
            when(machineStateDetectionSupport.isCompleted(any(SmartThingsDeviceStatusResDto.class), anyBoolean()))
                    .thenReturn(Optional.empty());
            when(machineStateDetectionSupport.isInterrupted(any(SmartThingsDeviceStatusResDto.class), anyBoolean()))
                    .thenReturn(true);
            when(reservation.getInterruptionCount()).thenReturn(ReservationConstants.INTERRUPTION_CONFIRM_THRESHOLD);

            // When
            reservationLifecycleProcessor.processRunningToCompleted(RESERVATION_ID, deviceStatus);

            // Then
            verify(reservation, times(1)).incrementInterruptionCount();
            verify(reservation, times(1)).cancel();
            verify(reservation, times(1)).clearInterruptionCount();
            verify(machine, times(1)).markAsAvailable();
            verify(reservationRepository, times(1)).save(reservation);
            verify(machineRepository, times(1)).save(machine);
            verify(reservationNotificationSupport, times(1)).sendInterruption(user, machine);
            verify(reservation, never()).complete();
            verify(reservationNotificationSupport, never()).sendCompletion(any(), any());
        }

        @Test
        @DisplayName("기기가 최초 일시정지되면 pausedAt을 기록한다")
        void shouldMarkPausedAt_WhenMachineFirstPaused() {
            // Given
            var deviceStatus = buildDeviceStatus(null);
            givenRunningReservation();
            when(machineStateDetectionSupport.isCompleted(any(SmartThingsDeviceStatusResDto.class), anyBoolean()))
                    .thenReturn(Optional.empty());
            when(machineStateDetectionSupport.isInterrupted(any(SmartThingsDeviceStatusResDto.class), anyBoolean()))
                    .thenReturn(false);
            when(machineStateDetectionSupport.isPaused(any(SmartThingsDeviceStatusResDto.class), anyBoolean()))
                    .thenReturn(true);
            when(reservation.getPausedAt()).thenReturn(null);

            // When
            reservationLifecycleProcessor.processRunningToCompleted(RESERVATION_ID, deviceStatus);

            // Then
            verify(reservation, times(1)).markAsPaused();
            verify(reservationRepository, times(1)).save(reservation);
            verify(reservation, never()).cancel();
            verify(reservationNotificationSupport, never()).sendPauseTimeout(any(), any());
        }

        @Test
        @DisplayName("일시정지가 10분 이상 지속되면 패널티 없이 CANCELLED로 전환하고 알림을 전송한다")
        void shouldCancelWithoutPenaltyAndNotify_WhenPausedTooLong() {
            // Given
            var deviceStatus = buildDeviceStatus(null);
            givenRunningReservation();
            when(reservation.getUser()).thenReturn(user);
            when(machineStateDetectionSupport.isCompleted(any(SmartThingsDeviceStatusResDto.class), anyBoolean()))
                    .thenReturn(Optional.empty());
            when(machineStateDetectionSupport.isInterrupted(any(SmartThingsDeviceStatusResDto.class), anyBoolean()))
                    .thenReturn(false);
            when(machineStateDetectionSupport.isPaused(any(SmartThingsDeviceStatusResDto.class), anyBoolean()))
                    .thenReturn(true);
            when(reservation.getPausedAt()).thenReturn(LocalDateTime.now().minusMinutes(11));

            // When
            reservationLifecycleProcessor.processRunningToCompleted(RESERVATION_ID, deviceStatus);

            // Then
            verify(reservation, times(1)).cancel();
            verify(reservation, times(1)).clearPausedAt();
            verify(machine, times(1)).markAsAvailable();
            verify(reservationRepository, times(1)).save(reservation);
            verify(machineRepository, times(1)).save(machine);
            verify(reservationNotificationSupport, times(1)).sendPauseTimeout(user, machine);
            verify(reservation, never()).complete();
            verify(reservation, never()).markAsPaused();
        }

        @Test
        @DisplayName("일시정지 후 재개되면 pausedAt을 초기화하고 예상 완료 시각을 갱신한다")
        void shouldClearPausedAtAndUpdateExpectedTime_WhenMachineResumed() {
            // Given
            var deviceStatus = buildDeviceStatus("2026-01-26T15:30:00Z");
            givenRunningReservation();
            when(machineStateDetectionSupport.isCompleted(any(SmartThingsDeviceStatusResDto.class), anyBoolean()))
                    .thenReturn(Optional.empty());
            when(machineStateDetectionSupport.isInterrupted(any(SmartThingsDeviceStatusResDto.class), anyBoolean()))
                    .thenReturn(false);
            when(machineStateDetectionSupport.isPaused(any(SmartThingsDeviceStatusResDto.class), anyBoolean()))
                    .thenReturn(false);
            when(reservation.getPausedAt()).thenReturn(LocalDateTime.now().minusMinutes(3));
            when(reservation.getExpectedCompletionTime()).thenReturn(null);

            // When
            reservationLifecycleProcessor.processRunningToCompleted(RESERVATION_ID, deviceStatus);

            // Then
            verify(reservation, times(1)).clearPausedAt();
            verify(reservation, times(1)).updateExpectedCompletionTime(any(LocalDateTime.class));
            verify(reservationRepository, times(1)).save(reservation);
            verify(reservation, never()).cancel();
        }

        @Test
        @DisplayName("재조회 시점에 RUNNING 상태가 아니면 처리하지 않는다")
        void shouldSkip_WhenNoLongerRunning() {
            // Given
            var deviceStatus = buildDeviceStatus(null);
            when(reservationRepository.findById(RESERVATION_ID)).thenReturn(Optional.of(reservation));
            when(reservation.isRunning()).thenReturn(false);

            // When
            reservationLifecycleProcessor.processRunningToCompleted(RESERVATION_ID, deviceStatus);

            // Then
            verify(reservation, never()).complete();
            verify(reservation, never()).cancel();
            verify(reservationRepository, never()).save(reservation);
        }
    }
}
