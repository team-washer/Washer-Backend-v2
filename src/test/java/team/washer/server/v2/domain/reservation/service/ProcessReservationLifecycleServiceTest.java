package team.washer.server.v2.domain.reservation.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
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
import team.washer.server.v2.domain.reservation.enums.ReservationStatus;
import team.washer.server.v2.domain.reservation.repository.ReservationRepository;
import team.washer.server.v2.domain.reservation.service.impl.ProcessReservationLifecycleServiceImpl;
import team.washer.server.v2.domain.smartthings.dto.response.SmartThingsDeviceStatusResDto;
import team.washer.server.v2.domain.smartthings.support.DeviceStatusQuerySupport;
import team.washer.server.v2.domain.smartthings.support.MachineStateDetectionSupport;
import team.washer.server.v2.domain.user.entity.User;

@ExtendWith(MockitoExtension.class)
class ProcessReservationLifecycleServiceTest {

    @InjectMocks
    private ProcessReservationLifecycleServiceImpl processReservationLifecycleService;

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private MachineRepository machineRepository;

    @Mock
    private MachineStateDetectionSupport machineStateDetectionSupport;

    @Mock
    private ReservationNotificationSupport reservationNotificationSupport;

    @Mock
    private DeviceStatusQuerySupport deviceStatusQuerySupport;

    @Mock
    private Reservation reservation;

    @Mock
    private Machine machine;

    @Mock
    private User user;

    private SmartThingsDeviceStatusResDto buildDeviceStatus(String completionTime) {
        var completionTimeAttr = new SmartThingsDeviceStatusResDto.AttributeState(completionTime, null, null);
        var washerOpState = new SmartThingsDeviceStatusResDto.WasherOperatingState(null, null, completionTimeAttr);
        var componentStatus = new SmartThingsDeviceStatusResDto.ComponentStatus(washerOpState, null, null);
        return new SmartThingsDeviceStatusResDto(Map.of("main", componentStatus));
    }

    @Nested
    @DisplayName("RESERVED -> RUNNING 전환 처리")
    class ProcessReservedToRunningTest {

        @Test
        @DisplayName("RESERVED 상태이고 기기가 작동 중이면 RUNNING으로 전환하고 시작 알림을 전송한다")
        void execute_ShouldStartReservation_WhenReservedAndMachineRunning() {
            // Given
            var deviceStatus = buildDeviceStatus("2026-01-26T15:30:00Z");
            when(reservationRepository.findByStatusWithMachineAndUser(ReservationStatus.RESERVED))
                    .thenReturn(List.of(reservation));
            when(reservationRepository.findByStatusWithMachineAndUser(ReservationStatus.RUNNING)).thenReturn(List.of());
            when(reservation.getMachine()).thenReturn(machine);
            when(reservation.getUser()).thenReturn(user);
            when(machine.getDeviceId()).thenReturn("device-123");
            when(deviceStatusQuerySupport.queryDeviceStatus("device-123")).thenReturn(deviceStatus);
            when(machineStateDetectionSupport.isRunning(any(SmartThingsDeviceStatusResDto.class))).thenReturn(true);

            // When
            processReservationLifecycleService.execute();

            // Then
            verify(reservation, times(1)).start(any(LocalDateTime.class));
            verify(reservationRepository, times(1)).save(reservation);
            verify(reservationNotificationSupport, times(1)).sendStarted(any(), any(), any(LocalDateTime.class));
        }

        @Test
        @DisplayName("RESERVED 상태이지만 기기가 작동 중이지 않으면 전환하지 않는다")
        void execute_ShouldNotStartReservation_WhenReservedButMachineNotRunning() {
            // Given
            var deviceStatus = buildDeviceStatus(null);
            when(reservationRepository.findByStatusWithMachineAndUser(ReservationStatus.RESERVED))
                    .thenReturn(List.of(reservation));
            when(reservationRepository.findByStatusWithMachineAndUser(ReservationStatus.RUNNING)).thenReturn(List.of());
            when(reservation.getMachine()).thenReturn(machine);
            when(machine.getDeviceId()).thenReturn("device-123");
            when(deviceStatusQuerySupport.queryDeviceStatus("device-123")).thenReturn(deviceStatus);
            when(machineStateDetectionSupport.isRunning(any(SmartThingsDeviceStatusResDto.class))).thenReturn(false);

            // When
            processReservationLifecycleService.execute();

            // Then
            verify(reservation, never()).start(any());
            verify(reservationRepository, never()).save(reservation);
        }
    }

    @Nested
    @DisplayName("RUNNING -> COMPLETED 전환 처리")
    class ProcessRunningToCompletedTest {

        @Test
        @DisplayName("RUNNING 상태이고 기기 작업이 완료되면 COMPLETED로 전환하고 알림을 전송한다")
        void execute_ShouldCompleteReservation_WhenRunningAndMachineCompleted() {
            // Given
            var deviceStatus = buildDeviceStatus("2026-01-26T15:30:00Z");
            when(reservationRepository.findByStatusWithMachineAndUser(ReservationStatus.RESERVED))
                    .thenReturn(List.of());
            when(reservationRepository.findByStatusWithMachineAndUser(ReservationStatus.RUNNING))
                    .thenReturn(List.of(reservation));
            when(reservation.getMachine()).thenReturn(machine);
            when(reservation.getUser()).thenReturn(user);
            when(machine.getDeviceId()).thenReturn("device-123");
            when(deviceStatusQuerySupport.queryDeviceStatus("device-123")).thenReturn(deviceStatus);
            when(machineStateDetectionSupport.isCompleted(any(SmartThingsDeviceStatusResDto.class)))
                    .thenReturn(Optional.of(LocalDateTime.now()));

            // When
            processReservationLifecycleService.execute();

            // Then
            verify(reservation, times(1)).complete();
            verify(reservationRepository, times(1)).save(reservation);
            verify(reservationNotificationSupport, times(1)).sendCompletion(user, machine);
        }

        @Test
        @DisplayName("RUNNING 상태이고 기기 작업이 완료되지 않으면 예상 완료 시각을 갱신한다")
        void execute_ShouldUpdateExpectedCompletionTime_WhenRunningAndMachineNotCompleted() {
            // Given
            var deviceStatus = buildDeviceStatus("2026-01-26T15:30:00Z");
            when(reservationRepository.findByStatusWithMachineAndUser(ReservationStatus.RESERVED))
                    .thenReturn(List.of());
            when(reservationRepository.findByStatusWithMachineAndUser(ReservationStatus.RUNNING))
                    .thenReturn(List.of(reservation));
            when(reservation.getMachine()).thenReturn(machine);
            when(machine.getDeviceId()).thenReturn("device-123");
            when(deviceStatusQuerySupport.queryDeviceStatus("device-123")).thenReturn(deviceStatus);
            when(machineStateDetectionSupport.isCompleted(any(SmartThingsDeviceStatusResDto.class)))
                    .thenReturn(Optional.empty());
            when(machineStateDetectionSupport.isInterrupted(any(SmartThingsDeviceStatusResDto.class))).thenReturn(false);
            when(machineStateDetectionSupport.isPaused(any(SmartThingsDeviceStatusResDto.class))).thenReturn(false);
            when(reservation.getExpectedCompletionTime()).thenReturn(null);

            // When
            processReservationLifecycleService.execute();

            // Then
            verify(reservation, never()).complete();
            verify(reservation, never()).cancel();
            verify(reservation, times(1)).updateExpectedCompletionTime(any(LocalDateTime.class));
            verify(reservationRepository, times(1)).save(reservation);
            verify(reservationNotificationSupport, never()).sendCompletion(any(), any());
        }

        @Test
        @DisplayName("RUNNING 상태에서 기기가 비정상 종료되면 패널티 없이 CANCELLED로 전환하고 중단 알림을 전송한다")
        void execute_ShouldCancelWithoutPenaltyAndNotify_WhenMachineInterrupted() {
            // Given
            var deviceStatus = buildDeviceStatus(null);
            when(reservationRepository.findByStatusWithMachineAndUser(ReservationStatus.RESERVED))
                    .thenReturn(List.of());
            when(reservationRepository.findByStatusWithMachineAndUser(ReservationStatus.RUNNING))
                    .thenReturn(List.of(reservation));
            when(reservation.getMachine()).thenReturn(machine);
            when(reservation.getUser()).thenReturn(user);
            when(machine.getDeviceId()).thenReturn("device-123");
            when(deviceStatusQuerySupport.queryDeviceStatus("device-123")).thenReturn(deviceStatus);
            when(machineStateDetectionSupport.isCompleted(any(SmartThingsDeviceStatusResDto.class)))
                    .thenReturn(Optional.empty());
            when(machineStateDetectionSupport.isInterrupted(any(SmartThingsDeviceStatusResDto.class))).thenReturn(true);

            // When
            processReservationLifecycleService.execute();

            // Then
            verify(reservation, times(1)).cancel();
            verify(machine, times(1)).markAsAvailable();
            verify(reservationRepository, times(1)).save(reservation);
            verify(machineRepository, times(1)).save(machine);
            verify(reservationNotificationSupport, times(1)).sendInterruption(user, machine);
            verify(reservation, never()).complete();
            verify(reservation, never()).updateExpectedCompletionTime(any());
            verify(reservationNotificationSupport, never()).sendCompletion(any(), any());
        }

        @Test
        @DisplayName("RUNNING 상태에서 기기가 최초 일시정지되면 pausedAt을 기록한다")
        void execute_ShouldMarkPausedAt_WhenMachineFirstPaused() {
            // Given
            var deviceStatus = buildDeviceStatus(null);
            when(reservationRepository.findByStatusWithMachineAndUser(ReservationStatus.RESERVED))
                    .thenReturn(List.of());
            when(reservationRepository.findByStatusWithMachineAndUser(ReservationStatus.RUNNING))
                    .thenReturn(List.of(reservation));
            when(reservation.getMachine()).thenReturn(machine);
            when(machine.getDeviceId()).thenReturn("device-123");
            when(deviceStatusQuerySupport.queryDeviceStatus("device-123")).thenReturn(deviceStatus);
            when(machineStateDetectionSupport.isCompleted(any(SmartThingsDeviceStatusResDto.class)))
                    .thenReturn(Optional.empty());
            when(machineStateDetectionSupport.isInterrupted(any(SmartThingsDeviceStatusResDto.class))).thenReturn(false);
            when(machineStateDetectionSupport.isPaused(any(SmartThingsDeviceStatusResDto.class))).thenReturn(true);
            when(reservation.getPausedAt()).thenReturn(null);

            // When
            processReservationLifecycleService.execute();

            // Then
            verify(reservation, times(1)).markAsPaused();
            verify(reservationRepository, times(1)).save(reservation);
            verify(reservation, never()).cancel();
            verify(reservationNotificationSupport, never()).sendPauseTimeout(any(), any());
        }

        @Test
        @DisplayName("RUNNING 상태에서 일시정지가 10분 이상 지속되면 패널티 없이 CANCELLED로 전환하고 알림을 전송한다")
        void execute_ShouldCancelWithoutPenaltyAndNotify_WhenPausedTooLong() {
            // Given
            var deviceStatus = buildDeviceStatus(null);
            when(reservationRepository.findByStatusWithMachineAndUser(ReservationStatus.RESERVED))
                    .thenReturn(List.of());
            when(reservationRepository.findByStatusWithMachineAndUser(ReservationStatus.RUNNING))
                    .thenReturn(List.of(reservation));
            when(reservation.getMachine()).thenReturn(machine);
            when(reservation.getUser()).thenReturn(user);
            when(machine.getDeviceId()).thenReturn("device-123");
            when(deviceStatusQuerySupport.queryDeviceStatus("device-123")).thenReturn(deviceStatus);
            when(machineStateDetectionSupport.isCompleted(any(SmartThingsDeviceStatusResDto.class)))
                    .thenReturn(Optional.empty());
            when(machineStateDetectionSupport.isInterrupted(any(SmartThingsDeviceStatusResDto.class))).thenReturn(false);
            when(machineStateDetectionSupport.isPaused(any(SmartThingsDeviceStatusResDto.class))).thenReturn(true);
            when(reservation.getPausedAt()).thenReturn(LocalDateTime.now().minusMinutes(11));

            // When
            processReservationLifecycleService.execute();

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
        @DisplayName("RUNNING 상태에서 일시정지 후 재개되면 pausedAt을 초기화하고 예상 완료 시각을 갱신한다")
        void execute_ShouldClearPausedAtAndUpdateExpectedTime_WhenMachineResumed() {
            // Given
            var deviceStatus = buildDeviceStatus("2026-01-26T15:30:00Z");
            when(reservationRepository.findByStatusWithMachineAndUser(ReservationStatus.RESERVED))
                    .thenReturn(List.of());
            when(reservationRepository.findByStatusWithMachineAndUser(ReservationStatus.RUNNING))
                    .thenReturn(List.of(reservation));
            when(reservation.getMachine()).thenReturn(machine);
            when(machine.getDeviceId()).thenReturn("device-123");
            when(deviceStatusQuerySupport.queryDeviceStatus("device-123")).thenReturn(deviceStatus);
            when(machineStateDetectionSupport.isCompleted(any(SmartThingsDeviceStatusResDto.class)))
                    .thenReturn(Optional.empty());
            when(machineStateDetectionSupport.isInterrupted(any(SmartThingsDeviceStatusResDto.class))).thenReturn(false);
            when(machineStateDetectionSupport.isPaused(any(SmartThingsDeviceStatusResDto.class))).thenReturn(false);
            when(reservation.getPausedAt()).thenReturn(LocalDateTime.now().minusMinutes(3));
            when(reservation.getExpectedCompletionTime()).thenReturn(null);

            // When
            processReservationLifecycleService.execute();

            // Then
            verify(reservation, times(1)).clearPausedAt();
            verify(reservation, times(1)).updateExpectedCompletionTime(any(LocalDateTime.class));
            verify(reservationRepository, times(1)).save(reservation);
            verify(reservation, never()).cancel();
        }
    }
}