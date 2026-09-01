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
import team.washer.server.v2.domain.reservation.support.CompletionDecision;
import team.washer.server.v2.domain.reservation.support.ReservationCompletionDecisionSupport;
import team.washer.server.v2.domain.reservation.support.ReservationStartDecisionSupport;
import team.washer.server.v2.domain.smartthings.dto.response.SmartThingsDeviceStatusResDto;
import team.washer.server.v2.domain.smartthings.support.MachineStateDetectionSupport;
import team.washer.server.v2.domain.user.entity.User;
import team.washer.server.v2.global.common.constants.ReservationConstants;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReservationLifecycleProcessor 개별 예약 처리")
class ReservationLifecycleProcessorTest {

    private static final Long RESERVATION_ID = 1L;
    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");

    @InjectMocks
    private ReservationLifecycleProcessor reservationLifecycleProcessor;

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private MachineRepository machineRepository;

    @Mock
    private MachineStateDetectionSupport machineStateDetectionSupport;

    @Mock
    private ReservationCompletionDecisionSupport completionDecisionSupport;

    @Mock
    private ReservationStartDecisionSupport reservationStartDecisionSupport;

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
        var dryerOpState = new SmartThingsDeviceStatusResDto.DryerOperatingState(null, null, completionTimeAttr);
        var componentStatus = new SmartThingsDeviceStatusResDto.ComponentStatus(washerOpState,
                dryerOpState,
                null,
                null);
        return new SmartThingsDeviceStatusResDto(Map.of("main", componentStatus));
    }

    private SmartThingsDeviceStatusResDto buildStatusWithMixedCompletionTime(String washerCompletionTime,
            String dryerCompletionTime) {
        var washerCompletionTimeAttr = new SmartThingsDeviceStatusResDto.AttributeState(washerCompletionTime,
                null,
                null);
        var dryerCompletionTimeAttr = new SmartThingsDeviceStatusResDto.AttributeState(dryerCompletionTime, null, null);
        var washerOpState = new SmartThingsDeviceStatusResDto.WasherOperatingState(null,
                null,
                washerCompletionTimeAttr);
        var dryerOpState = new SmartThingsDeviceStatusResDto.DryerOperatingState(null, null, dryerCompletionTimeAttr);
        var componentStatus = new SmartThingsDeviceStatusResDto.ComponentStatus(washerOpState,
                dryerOpState,
                null,
                null);
        return new SmartThingsDeviceStatusResDto(Map.of("main", componentStatus));
    }

    @Nested
    @DisplayName("RESERVED -> RUNNING 전환 처리")
    class ProcessReservedToRunningTest {

        @Test
        @DisplayName("RESERVED 상태이고 기기가 작동 중이면 RUNNING으로 전환하고 시작 알림을 전송한다")
        void shouldStartReservation_WhenReservedAndMachineRunning() {
            // Given
            var expectedCompletionTime = LocalDateTime.of(2026, 1, 27, 0, 30);
            var deviceStatus = buildDeviceStatus("2026-01-26T15:30:00Z");
            when(reservationRepository.findByIdForUpdate(RESERVATION_ID)).thenReturn(Optional.of(reservation));
            when(reservation.isReserved()).thenReturn(true);
            when(reservation.getMachine()).thenReturn(machine);
            when(reservation.getUser()).thenReturn(user);
            when(reservation.getExpectedCompletionTime()).thenReturn(expectedCompletionTime);
            when(reservationStartDecisionSupport.isStarted(any(SmartThingsDeviceStatusResDto.class), anyBoolean()))
                    .thenReturn(true);

            // When
            reservationLifecycleProcessor.processReservedToRunning(RESERVATION_ID, deviceStatus);

            // Then
            verify(reservation, times(1)).start(any(LocalDateTime.class));
            verify(reservationRepository, times(1)).save(reservation);
            verify(reservationNotificationSupport, times(1)).sendStarted(user, machine, expectedCompletionTime);
        }

        @Test
        @DisplayName("RESERVED 상태이지만 기기가 작동 중이지 않으면 전환하지 않는다")
        void shouldNotStartReservation_WhenReservedButMachineNotRunning() {
            // Given
            var deviceStatus = buildDeviceStatus(null);
            when(reservationRepository.findByIdForUpdate(RESERVATION_ID)).thenReturn(Optional.of(reservation));
            when(reservation.isReserved()).thenReturn(true);
            when(reservation.getMachine()).thenReturn(machine);
            when(reservationStartDecisionSupport.isStarted(any(SmartThingsDeviceStatusResDto.class), anyBoolean()))
                    .thenReturn(false);

            // When
            reservationLifecycleProcessor.processReservedToRunning(RESERVATION_ID, deviceStatus);

            // Then
            verify(reservation, never()).start(any());
            verify(reservationRepository, never()).save(reservation);
        }

        @Test
        @DisplayName("건조기 예약 시작 시 건조기 완료 예정 시간을 저장해야 한다")
        void shouldStartDryerReservationWithDryerCompletionTime_WhenBothCompletionTimesExist() {
            // Given
            var dryerCompletionTime = LocalDateTime.of(2026, 1, 27, 1, 0);
            var deviceStatus = buildStatusWithMixedCompletionTime("2026-01-26T15:30:00Z", "2026-01-26T16:00:00Z");
            when(reservationRepository.findByIdForUpdate(RESERVATION_ID)).thenReturn(Optional.of(reservation));
            when(reservation.isReserved()).thenReturn(true);
            when(reservation.getMachine()).thenReturn(machine);
            when(reservation.getUser()).thenReturn(user);
            when(reservation.getExpectedCompletionTime()).thenReturn(dryerCompletionTime);
            when(machine.isWasher()).thenReturn(false);
            when(reservationStartDecisionSupport.isStarted(any(SmartThingsDeviceStatusResDto.class), anyBoolean()))
                    .thenReturn(true);

            // When
            reservationLifecycleProcessor.processReservedToRunning(RESERVATION_ID, deviceStatus);

            // Then
            verify(reservation, times(1)).start(dryerCompletionTime);
            verify(reservationNotificationSupport, times(1)).sendStarted(user, machine, dryerCompletionTime);
        }

        @Test
        @DisplayName("기기가 보고한 완료 예정 시각이 상한을 벗어나 저장되지 않으면 예상 완료 시각 없이 시작 알림을 전송한다")
        void shouldSendStartedWithoutExpectedTime_WhenReportedCompletionTimeRejected() {
            // Given
            var deviceStatus = buildDeviceStatus("2026-01-26T15:30:00Z");
            when(reservationRepository.findByIdForUpdate(RESERVATION_ID)).thenReturn(Optional.of(reservation));
            when(reservation.isReserved()).thenReturn(true);
            when(reservation.getMachine()).thenReturn(machine);
            when(reservation.getUser()).thenReturn(user);
            when(reservation.getExpectedCompletionTime()).thenReturn(null);
            when(reservationStartDecisionSupport.isStarted(any(SmartThingsDeviceStatusResDto.class), anyBoolean()))
                    .thenReturn(true);

            // When
            reservationLifecycleProcessor.processReservedToRunning(RESERVATION_ID, deviceStatus);

            // Then
            verify(reservation, times(1)).start(any(LocalDateTime.class));
            verify(reservationNotificationSupport, times(1)).sendStarted(user, machine, null);
        }

        @Test
        @DisplayName("재조회 시점에 RESERVED 상태가 아니면 처리하지 않는다")
        void shouldSkip_WhenNoLongerReserved() {
            // Given
            var deviceStatus = buildDeviceStatus(null);
            when(reservationRepository.findByIdForUpdate(RESERVATION_ID)).thenReturn(Optional.of(reservation));
            when(reservation.isReserved()).thenReturn(false);

            // When
            reservationLifecycleProcessor.processReservedToRunning(RESERVATION_ID, deviceStatus);

            // Then
            verify(reservation, never()).start(any());
            verify(reservationRepository, never()).save(reservation);
        }

        @Test
        @DisplayName("RESERVED 상태여도 이미 만료된 예약이면 RUNNING으로 전환하지 않는다")
        void shouldSkip_WhenReservedButExpired() {
            // Given
            var deviceStatus = buildDeviceStatus("2026-01-26T15:30:00Z");
            when(reservationRepository.findByIdForUpdate(RESERVATION_ID)).thenReturn(Optional.of(reservation));
            when(reservation.isReserved()).thenReturn(true);
            when(reservation.isExpired()).thenReturn(true);

            // When
            reservationLifecycleProcessor.processReservedToRunning(RESERVATION_ID, deviceStatus);

            // Then
            verify(reservation, never()).start(any());
            verify(machineStateDetectionSupport, never()).isRunning(any(), anyBoolean());
            verify(reservationRepository, never()).save(reservation);
        }
    }

    @Nested
    @DisplayName("RUNNING -> COMPLETED 전환 처리")
    class ProcessRunningToCompletedTest {

        private void givenRunningReservation() {
            when(reservationRepository.findByIdForUpdate(RESERVATION_ID)).thenReturn(Optional.of(reservation));
            when(reservation.isRunning()).thenReturn(true);
            when(reservation.getMachine()).thenReturn(machine);
        }

        private void givenCompletionDecision(CompletionDecision decision) {
            when(completionDecisionSupport.decide(any(), any(), anyBoolean())).thenReturn(decision);
        }

        @Test
        @DisplayName("완료 판정이 확정 임계치까지 연속되면 COMPLETED로 전환하고 완료 알림을 전송한다")
        void shouldCompleteReservation_WhenCompletionConfirmed() {
            // Given
            var deviceStatus = buildDeviceStatus("2026-01-26T15:30:00Z");
            givenRunningReservation();
            givenCompletionDecision(
                    CompletionDecision.completed(LocalDateTime.now(KOREA_ZONE), "smartthings_completed"));
            when(reservation.getCompletionCount()).thenReturn(ReservationConstants.COMPLETION_CONFIRM_THRESHOLD);
            when(reservation.getUser()).thenReturn(user);

            // When
            reservationLifecycleProcessor.processRunningToCompleted(RESERVATION_ID, deviceStatus);

            // Then
            verify(reservation, times(1)).incrementCompletionCount();
            verify(reservation, times(1)).complete();
            verify(reservation, times(1)).clearCompletionCount();
            verify(reservation, times(1)).clearInterruptionCount();
            verify(reservation, times(1)).clearPausedAt();
            verify(machine, times(1)).markAsAvailable();
            verify(reservationRepository, times(1)).save(reservation);
            verify(machineRepository, times(1)).save(machine);
            verify(reservationNotificationSupport, times(1)).sendCompletion(user, machine);
        }

        @Test
        @DisplayName("완료가 처음 감지되면 카운트만 증가시키고 완료 처리하지 않는다")
        void shouldOnlyIncrementCount_WhenCompletionBelowThreshold() {
            // Given
            var deviceStatus = buildDeviceStatus("2026-01-26T15:30:00Z");
            givenRunningReservation();
            givenCompletionDecision(
                    CompletionDecision.completed(LocalDateTime.now(KOREA_ZONE), "stopped_near_completion"));
            when(reservation.getCompletionCount()).thenReturn(ReservationConstants.COMPLETION_CONFIRM_THRESHOLD - 1);

            // When
            reservationLifecycleProcessor.processRunningToCompleted(RESERVATION_ID, deviceStatus);

            // Then
            verify(reservation, times(1)).incrementCompletionCount();
            verify(reservation, never()).complete();
            verify(machine, never()).markAsAvailable();
            verify(reservationRepository, times(1)).save(reservation);
            verify(machineRepository, never()).save(machine);
            verify(reservationNotificationSupport, never()).sendCompletion(any(), any());
        }

        @Test
        @DisplayName("완료 신호가 가드에 걸려 보류되면 완료 카운트를 초기화하고 중단 판정으로 넘어가지 않는다")
        void shouldClearCompletionCountAndDefer_WhenCompletionDeferredByGuard() {
            // Given
            var deviceStatus = buildDeviceStatus("2026-01-26T15:30:00Z");
            givenRunningReservation();
            givenCompletionDecision(CompletionDecision.deferred(LocalDateTime.now(KOREA_ZONE), "too_early_completion"));
            when(reservation.getCompletionCount()).thenReturn(1);

            // When
            reservationLifecycleProcessor.processRunningToCompleted(RESERVATION_ID, deviceStatus);

            // Then
            verify(reservation, times(1)).clearCompletionCount();
            verify(reservation, never()).complete();
            verify(reservation, never()).incrementCompletionCount();
            verify(reservationRepository, times(1)).save(reservation);
            verify(machineStateDetectionSupport, never()).isInterrupted(any(), anyBoolean());
            verify(reservationNotificationSupport, never()).sendCompletion(any(), any());
        }

        @Test
        @DisplayName("전원이 꺼지면 완료 예정 시각 근처여도 중단 카운트를 증가시킨다")
        void shouldCountInterruption_WhenPoweredOffEvenNearCompletionTime() {
            // Given
            var deviceStatus = buildDeviceStatus(null);
            givenRunningReservation();
            givenCompletionDecision(CompletionDecision.notCompleted());
            when(machineStateDetectionSupport.isPoweredOff(any(SmartThingsDeviceStatusResDto.class))).thenReturn(true);
            when(reservation.getInterruptionCount()).thenReturn(1);

            // When
            reservationLifecycleProcessor.processRunningToCompleted(RESERVATION_ID, deviceStatus);

            // Then
            verify(reservation, times(1)).incrementInterruptionCount();
            verify(reservation, never()).clearInterruptionCount();
            verify(reservation, never()).cancel();
            verify(reservationRepository, times(1)).save(reservation);
            verify(completionDecisionSupport, never()).isStoppedNearCompletion(any(), any(), anyBoolean());
        }

        @Test
        @DisplayName("완료 예정 시각 근처 정지는 중단 카운트를 건드리지 않고 판정을 보류한다")
        void shouldHoldWithoutTouchingInterruptionCount_WhenStoppedNearCompletionTime() {
            // Given
            var deviceStatus = buildDeviceStatus(null);
            givenRunningReservation();
            givenCompletionDecision(CompletionDecision.notCompleted());
            when(machineStateDetectionSupport.isPoweredOff(any(SmartThingsDeviceStatusResDto.class))).thenReturn(false);
            when(completionDecisionSupport.isStoppedNearCompletion(any(), any(), anyBoolean())).thenReturn(true);

            // When
            reservationLifecycleProcessor.processRunningToCompleted(RESERVATION_ID, deviceStatus);

            // Then
            verify(reservation, never()).incrementInterruptionCount();
            verify(reservation, never()).clearInterruptionCount();
            verify(reservation, never()).cancel();
            verify(reservation, never()).complete();
            verify(reservationRepository, never()).save(reservation);
            verify(machineStateDetectionSupport, never()).isInterrupted(any(), anyBoolean());
        }

        @Test
        @DisplayName("중단이 감지되어도 확정 임계치 미만이면 카운트만 증가시키고 취소하지 않는다")
        void shouldOnlyIncrementCount_WhenInterruptionBelowThreshold() {
            // Given
            var deviceStatus = buildDeviceStatus(null);
            givenRunningReservation();
            givenCompletionDecision(CompletionDecision.notCompleted());
            when(machineStateDetectionSupport.isPoweredOff(any(SmartThingsDeviceStatusResDto.class))).thenReturn(false);
            when(completionDecisionSupport.isStoppedNearCompletion(any(), any(), anyBoolean())).thenReturn(false);
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
            givenCompletionDecision(CompletionDecision.notCompleted());
            when(reservation.getUser()).thenReturn(user);
            when(machineStateDetectionSupport.isPoweredOff(any(SmartThingsDeviceStatusResDto.class))).thenReturn(false);
            when(completionDecisionSupport.isStoppedNearCompletion(any(), any(), anyBoolean())).thenReturn(false);
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
            givenCompletionDecision(CompletionDecision.notCompleted());
            when(machineStateDetectionSupport.isPoweredOff(any(SmartThingsDeviceStatusResDto.class))).thenReturn(false);
            when(completionDecisionSupport.isStoppedNearCompletion(any(), any(), anyBoolean())).thenReturn(false);
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
            givenCompletionDecision(CompletionDecision.notCompleted());
            when(reservation.getUser()).thenReturn(user);
            when(machineStateDetectionSupport.isPoweredOff(any(SmartThingsDeviceStatusResDto.class))).thenReturn(false);
            when(completionDecisionSupport.isStoppedNearCompletion(any(), any(), anyBoolean())).thenReturn(false);
            when(machineStateDetectionSupport.isInterrupted(any(SmartThingsDeviceStatusResDto.class), anyBoolean()))
                    .thenReturn(false);
            when(machineStateDetectionSupport.isPaused(any(SmartThingsDeviceStatusResDto.class), anyBoolean()))
                    .thenReturn(true);
            when(reservation.getPausedAt()).thenReturn(LocalDateTime.now(KOREA_ZONE).minusMinutes(11));

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
        @DisplayName("RUNNING 상태이고 기기 작업이 완료되지 않으면 예상 완료 시각을 갱신한다")
        void shouldUpdateExpectedCompletionTime_WhenRunningAndMachineNotCompleted() {
            // Given
            var deviceStatus = buildDeviceStatus("2026-01-26T15:30:00Z");
            givenRunningReservation();
            givenCompletionDecision(CompletionDecision.notCompleted());
            when(machineStateDetectionSupport.isPoweredOff(any(SmartThingsDeviceStatusResDto.class))).thenReturn(false);
            when(completionDecisionSupport.isStoppedNearCompletion(any(), any(), anyBoolean())).thenReturn(false);
            when(machineStateDetectionSupport.isInterrupted(any(SmartThingsDeviceStatusResDto.class), anyBoolean()))
                    .thenReturn(false);
            when(machineStateDetectionSupport.isPaused(any(SmartThingsDeviceStatusResDto.class), anyBoolean()))
                    .thenReturn(false);
            when(reservation.getExpectedCompletionTime()).thenReturn(null);
            when(reservation.updateExpectedCompletionTime(any(LocalDateTime.class))).thenReturn(true);

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
        @DisplayName("건조기 진행 중 상태 갱신 시 건조기 완료 예정 시간을 저장해야 한다")
        void shouldUpdateDryerExpectedCompletionTime_WhenBothCompletionTimesExist() {
            // Given
            var deviceStatus = buildStatusWithMixedCompletionTime("2026-01-26T15:30:00Z", "2026-01-26T16:00:00Z");
            givenRunningReservation();
            givenCompletionDecision(CompletionDecision.notCompleted());
            when(machine.isWasher()).thenReturn(false);
            when(machineStateDetectionSupport.isPoweredOff(any(SmartThingsDeviceStatusResDto.class))).thenReturn(false);
            when(completionDecisionSupport.isStoppedNearCompletion(any(), any(), anyBoolean())).thenReturn(false);
            when(machineStateDetectionSupport.isInterrupted(any(SmartThingsDeviceStatusResDto.class), anyBoolean()))
                    .thenReturn(false);
            when(machineStateDetectionSupport.isPaused(any(SmartThingsDeviceStatusResDto.class), anyBoolean()))
                    .thenReturn(false);
            when(reservation.getExpectedCompletionTime()).thenReturn(null);
            when(reservation.updateExpectedCompletionTime(any(LocalDateTime.class))).thenReturn(true);

            // When
            reservationLifecycleProcessor.processRunningToCompleted(RESERVATION_ID, deviceStatus);

            // Then
            verify(reservation, times(1)).updateExpectedCompletionTime(LocalDateTime.of(2026, 1, 27, 1, 0));
            verify(reservationRepository, times(1)).save(reservation);
        }

        @Test
        @DisplayName("기기가 보고한 완료 예정 시각이 상한을 벗어나 거부되면 저장하지 않는다")
        void shouldNotSave_WhenReportedExpectedCompletionTimeRejected() {
            // Given
            var deviceStatus = buildDeviceStatus("2026-01-26T15:30:00Z");
            givenRunningReservation();
            givenCompletionDecision(CompletionDecision.notCompleted());
            when(machineStateDetectionSupport.isPoweredOff(any(SmartThingsDeviceStatusResDto.class))).thenReturn(false);
            when(completionDecisionSupport.isStoppedNearCompletion(any(), any(), anyBoolean())).thenReturn(false);
            when(machineStateDetectionSupport.isInterrupted(any(SmartThingsDeviceStatusResDto.class), anyBoolean()))
                    .thenReturn(false);
            when(machineStateDetectionSupport.isPaused(any(SmartThingsDeviceStatusResDto.class), anyBoolean()))
                    .thenReturn(false);
            when(reservation.getExpectedCompletionTime()).thenReturn(null);
            when(reservation.updateExpectedCompletionTime(any(LocalDateTime.class))).thenReturn(false);

            // When
            reservationLifecycleProcessor.processRunningToCompleted(RESERVATION_ID, deviceStatus);

            // Then
            verify(reservation, times(1)).updateExpectedCompletionTime(any(LocalDateTime.class));
            verify(reservationRepository, never()).save(reservation);
        }

        @Test
        @DisplayName("일시정지 후 재개되면 pausedAt을 초기화하고 예상 완료 시각을 갱신한다")
        void shouldClearPausedAtAndUpdateExpectedTime_WhenMachineResumed() {
            // Given
            var deviceStatus = buildDeviceStatus("2026-01-26T15:30:00Z");
            givenRunningReservation();
            givenCompletionDecision(CompletionDecision.notCompleted());
            when(machineStateDetectionSupport.isPoweredOff(any(SmartThingsDeviceStatusResDto.class))).thenReturn(false);
            when(completionDecisionSupport.isStoppedNearCompletion(any(), any(), anyBoolean())).thenReturn(false);
            when(machineStateDetectionSupport.isInterrupted(any(SmartThingsDeviceStatusResDto.class), anyBoolean()))
                    .thenReturn(false);
            when(machineStateDetectionSupport.isPaused(any(SmartThingsDeviceStatusResDto.class), anyBoolean()))
                    .thenReturn(false);
            when(reservation.getPausedAt()).thenReturn(LocalDateTime.now(KOREA_ZONE).minusMinutes(3));
            when(reservation.getExpectedCompletionTime()).thenReturn(null);
            when(reservation.updateExpectedCompletionTime(any(LocalDateTime.class))).thenReturn(true);

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
            when(reservationRepository.findByIdForUpdate(RESERVATION_ID)).thenReturn(Optional.of(reservation));
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
