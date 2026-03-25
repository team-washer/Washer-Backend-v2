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
import team.washer.server.v2.domain.notification.service.SendCompletionNotificationService;
import team.washer.server.v2.domain.reservation.entity.Reservation;
import team.washer.server.v2.domain.reservation.enums.ReservationStatus;
import team.washer.server.v2.domain.reservation.repository.ReservationRepository;
import team.washer.server.v2.domain.reservation.service.impl.ProcessReservationLifecycleServiceImpl;
import team.washer.server.v2.domain.smartthings.dto.response.SmartThingsDeviceStatusResDto;
import team.washer.server.v2.domain.smartthings.service.DetectMachineCompletionService;
import team.washer.server.v2.domain.smartthings.service.DetectMachineInterruptedService;
import team.washer.server.v2.domain.smartthings.service.DetectMachineRunningService;
import team.washer.server.v2.domain.smartthings.service.QueryDeviceStatusService;
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
    private DetectMachineRunningService detectMachineRunningService;

    @Mock
    private DetectMachineCompletionService detectMachineCompletionService;

    @Mock
    private DetectMachineInterruptedService detectMachineInterruptedService;

    @Mock
    private QueryDeviceStatusService queryDeviceStatusService;

    @Mock
    private SendCompletionNotificationService sendCompletionNotificationService;

    @Mock
    private Reservation reservation;

    @Mock
    private Machine machine;

    @Mock
    private User user;

    @Nested
    @DisplayName("CONFIRMED -> RUNNING 전환 처리")
    class ProcessConfirmedToRunningTest {

        @Test
        @DisplayName("CONFIRMED 상태이고 기기가 작동 중이면 RUNNING으로 전환한다")
        void execute_ShouldStartReservation_WhenConfirmedAndMachineRunning() {
            // Given
            when(reservationRepository.findByStatusWithMachineAndUser(ReservationStatus.CONFIRMED))
                    .thenReturn(List.of(reservation));
            when(reservation.getMachine()).thenReturn(machine);
            when(machine.getDeviceId()).thenReturn("device-123");
            when(detectMachineRunningService.execute("device-123")).thenReturn(true);

            // SmartThings 상태 Mock
            var completionTimeAttr = new SmartThingsDeviceStatusResDto.AttributeState("2026-01-26T15:30:00Z",
                    "2026-01-26T14:30:00Z",
                    null);
            var washerOpState = new SmartThingsDeviceStatusResDto.WasherOperatingState(null, null, completionTimeAttr);
            var componentStatus = new SmartThingsDeviceStatusResDto.ComponentStatus(washerOpState, null, null);
            var deviceStatus = new SmartThingsDeviceStatusResDto(Map.of("main", componentStatus));
            when(queryDeviceStatusService.execute("device-123")).thenReturn(deviceStatus);

            // When
            processReservationLifecycleService.execute();

            // Then
            verify(reservation, times(1)).start(any(LocalDateTime.class));
            verify(reservationRepository, times(1)).save(reservation);
        }

        @Test
        @DisplayName("CONFIRMED 상태이지만 기기가 작동 중이지 않으면 전환하지 않는다")
        void execute_ShouldNotStartReservation_WhenConfirmedButMachineNotRunning() {
            // Given
            when(reservationRepository.findByStatusWithMachineAndUser(ReservationStatus.CONFIRMED))
                    .thenReturn(List.of(reservation));
            when(reservation.getMachine()).thenReturn(machine);
            when(machine.getDeviceId()).thenReturn("device-123");
            when(detectMachineRunningService.execute("device-123")).thenReturn(false);

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
            when(reservationRepository.findByStatusWithMachineAndUser(ReservationStatus.CONFIRMED))
                    .thenReturn(List.of());
            when(reservationRepository.findByStatusWithMachineAndUser(ReservationStatus.RUNNING))
                    .thenReturn(List.of(reservation));
            when(reservation.getMachine()).thenReturn(machine);
            when(reservation.getUser()).thenReturn(user);
            when(machine.getDeviceId()).thenReturn("device-123");
            when(detectMachineCompletionService.execute("device-123")).thenReturn(Optional.of(LocalDateTime.now()));

            // When
            processReservationLifecycleService.execute();

            // Then
            verify(reservation, times(1)).complete();
            verify(reservationRepository, times(1)).save(reservation);
            verify(sendCompletionNotificationService, times(1)).execute(user, machine);
        }

        @Test
        @DisplayName("RUNNING 상태이고 기기 작업이 완료되지 않으면 예상 완료 시각을 갱신한다")
        void execute_ShouldUpdateExpectedCompletionTime_WhenRunningAndMachineNotCompleted() {
            // Given
            when(reservationRepository.findByStatusWithMachineAndUser(ReservationStatus.CONFIRMED))
                    .thenReturn(List.of());
            when(reservationRepository.findByStatusWithMachineAndUser(ReservationStatus.RUNNING))
                    .thenReturn(List.of(reservation));
            when(reservation.getMachine()).thenReturn(machine);
            when(machine.getDeviceId()).thenReturn("device-123");
            when(detectMachineCompletionService.execute("device-123")).thenReturn(Optional.empty());
            when(detectMachineInterruptedService.execute("device-123")).thenReturn(false);

            var completionTimeAttr = new SmartThingsDeviceStatusResDto.AttributeState("2026-01-26T15:30:00Z",
                    "2026-01-26T14:30:00Z",
                    null);
            var washerOpState = new SmartThingsDeviceStatusResDto.WasherOperatingState(null, null, completionTimeAttr);
            var componentStatus = new SmartThingsDeviceStatusResDto.ComponentStatus(washerOpState, null, null);
            var deviceStatus = new SmartThingsDeviceStatusResDto(Map.of("main", componentStatus));
            when(queryDeviceStatusService.execute("device-123")).thenReturn(deviceStatus);

            // When
            processReservationLifecycleService.execute();

            // Then
            verify(reservation, never()).complete();
            verify(reservation, never()).cancel();
            verify(reservation, times(1)).updateExpectedCompletionTime(any(LocalDateTime.class));
            verify(reservationRepository, times(1)).save(reservation);
            verify(sendCompletionNotificationService, never()).execute(any(), any());
        }

        @Test
        @DisplayName("RUNNING 상태에서 기기가 비정상 종료되면 패널티 없이 CANCELLED로 전환한다")
        void execute_ShouldCancelWithoutPenalty_WhenMachineInterrupted() {
            // Given
            when(reservationRepository.findByStatusWithMachineAndUser(ReservationStatus.CONFIRMED))
                    .thenReturn(List.of());
            when(reservationRepository.findByStatusWithMachineAndUser(ReservationStatus.RUNNING))
                    .thenReturn(List.of(reservation));
            when(reservation.getMachine()).thenReturn(machine);
            when(machine.getDeviceId()).thenReturn("device-123");
            when(detectMachineCompletionService.execute("device-123")).thenReturn(Optional.empty());
            when(detectMachineInterruptedService.execute("device-123")).thenReturn(true);

            // When
            processReservationLifecycleService.execute();

            // Then
            verify(reservation, times(1)).cancel();
            verify(machine, times(1)).markAsAvailable();
            verify(reservationRepository, times(1)).save(reservation);
            verify(machineRepository, times(1)).save(machine);
            verify(reservation, never()).complete();
            verify(reservation, never()).updateExpectedCompletionTime(any());
            verify(sendCompletionNotificationService, never()).execute(any(), any());
        }
    }
}
