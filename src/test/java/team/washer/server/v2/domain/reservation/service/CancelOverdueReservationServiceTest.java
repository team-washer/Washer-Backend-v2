package team.washer.server.v2.domain.reservation.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import team.washer.server.v2.domain.machine.entity.Machine;
import team.washer.server.v2.domain.reservation.entity.Reservation;
import team.washer.server.v2.domain.reservation.enums.ReservationStatus;
import team.washer.server.v2.domain.reservation.repository.ReservationRepository;
import team.washer.server.v2.domain.reservation.service.impl.CancelOverdueReservationServiceImpl;
import team.washer.server.v2.domain.reservation.util.PenaltyRedisUtil;
import team.washer.server.v2.domain.smartthings.dto.response.SmartThingsDeviceStatusResDto;
import team.washer.server.v2.domain.smartthings.service.DetectMachineRunningService;
import team.washer.server.v2.domain.smartthings.service.QueryDeviceStatusService;
import team.washer.server.v2.domain.user.entity.User;

@ExtendWith(MockitoExtension.class)
class CancelOverdueReservationServiceTest {

    @InjectMocks
    private CancelOverdueReservationServiceImpl cancelOverdueReservationService;

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private PenaltyRedisUtil penaltyRedisUtil;

    @Mock
    private DetectMachineRunningService detectMachineRunningService;

    @Mock
    private QueryDeviceStatusService queryDeviceStatusService;

    @Mock
    private Reservation reservation;

    @Mock
    private Machine machine;

    @Mock
    private User user;

    @Test
    @DisplayName("만료된 RESERVED 예약이 있고 기기가 작동 중이지 않으면 취소하고 패널티를 부여한다")
    void execute_ShouldCancelAndPenalize_WhenExpiredReservedExistsAndMachineNotRunning() {
        // Given
        when(reservationRepository.findExpiredReservations(eq(ReservationStatus.RESERVED),
                any(LocalDateTime.class),
                any(LocalDateTime.class))).thenReturn(List.of(reservation));

        when(reservation.getMachine()).thenReturn(machine);
        when(machine.getDeviceId()).thenReturn("device-123");
        when(detectMachineRunningService.execute("device-123")).thenReturn(false);
        when(reservation.getUser()).thenReturn(user);

        // When
        cancelOverdueReservationService.execute();

        // Then
        verify(reservation, times(1)).cancel();
        verify(reservationRepository, times(1)).save(reservation);
        verify(penaltyRedisUtil, times(1)).applyPenalty(user);
        verify(reservation, never()).start(any());
    }

    @Test
    @DisplayName("만료된 RESERVED 예약이 있고 기기가 작동 중이면 자동으로 시작한다")
    void execute_ShouldAutoStart_WhenExpiredReservedExistsAndMachineRunning() {
        // Given
        when(reservationRepository.findExpiredReservations(eq(ReservationStatus.RESERVED),
                any(LocalDateTime.class),
                any(LocalDateTime.class))).thenReturn(List.of(reservation));

        when(reservation.getMachine()).thenReturn(machine);
        when(machine.getDeviceId()).thenReturn("device-123");
        when(detectMachineRunningService.execute("device-123")).thenReturn(true);

        // SmartThingsDeviceStatusResDto Mock 생성
        var completionTimeValue = new SmartThingsDeviceStatusResDto.Value("2026-01-26T15:30:00Z",
                "2026-01-26T14:30:00Z",
                null);
        var completionTimeCapability = new SmartThingsDeviceStatusResDto.CapabilityStatus(completionTimeValue);
        var componentStatus = new SmartThingsDeviceStatusResDto.ComponentStatus(null,
                null,
                null,
                null,
                completionTimeCapability,
                null);
        var deviceStatus = new SmartThingsDeviceStatusResDto(java.util.Map.of("main", componentStatus));
        when(queryDeviceStatusService.execute("device-123")).thenReturn(deviceStatus);

        // When
        cancelOverdueReservationService.execute();

        // Then
        verify(reservation, times(1)).start(any(LocalDateTime.class));
        verify(reservationRepository, times(1)).save(reservation);
        verify(reservation, never()).cancel();
        verify(penaltyRedisUtil, never()).applyPenalty(any());
    }

    @Test
    @DisplayName("만료된 예약이 없으면 아무 동작도 하지 않는다")
    void execute_ShouldDoNothing_WhenNoExpiredReservations() {
        // Given
        when(reservationRepository.findExpiredReservations(eq(ReservationStatus.RESERVED),
                any(LocalDateTime.class),
                any(LocalDateTime.class))).thenReturn(Collections.emptyList());

        // When
        cancelOverdueReservationService.execute();

        // Then
        verify(reservation, never()).cancel();
        verify(reservationRepository, never()).save(any());
        verify(penaltyRedisUtil, never()).applyPenalty(any());
    }
}
