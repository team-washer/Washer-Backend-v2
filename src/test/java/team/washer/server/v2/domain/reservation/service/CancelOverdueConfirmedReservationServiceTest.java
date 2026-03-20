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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import jakarta.persistence.EntityManager;
import team.washer.server.v2.domain.machine.entity.Machine;
import team.washer.server.v2.domain.machine.repository.MachineRepository;
import team.washer.server.v2.domain.reservation.entity.Reservation;
import team.washer.server.v2.domain.reservation.enums.ReservationStatus;
import team.washer.server.v2.domain.reservation.repository.ReservationRepository;
import team.washer.server.v2.domain.reservation.service.impl.CancelOverdueConfirmedReservationServiceImpl;
import team.washer.server.v2.domain.reservation.util.PenaltyRedisUtil;
import team.washer.server.v2.domain.smartthings.dto.response.SmartThingsDeviceStatusResDto;
import team.washer.server.v2.domain.smartthings.service.DetectMachineRunningService;
import team.washer.server.v2.domain.smartthings.service.QueryDeviceStatusService;
import team.washer.server.v2.domain.user.entity.User;

@ExtendWith(MockitoExtension.class)
class CancelOverdueConfirmedReservationServiceTest {

    @InjectMocks
    private CancelOverdueConfirmedReservationServiceImpl cancelOverdueConfirmedReservationService;

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private PenaltyRedisUtil penaltyRedisUtil;

    @Mock
    private DetectMachineRunningService detectMachineRunningService;

    @Mock
    private QueryDeviceStatusService queryDeviceStatusService;

    @Mock
    private MachineRepository machineRepository;

    @Mock
    private EntityManager entityManager;

    @Mock
    private Reservation reservation;

    @Mock
    private Machine machine;

    @Mock
    private User user;

    @Nested
    @DisplayName("만료된 CONFIRMED 예약이 없으면")
    class NoExpiredReservations {

        @Test
        @DisplayName("아무 동작도 하지 않는다")
        void execute_ShouldDoNothing_WhenNoExpiredConfirmedReservations() {
            // Given
            when(reservationRepository.findExpiredConfirmedReservations(eq(ReservationStatus.CONFIRMED),
                    any(LocalDateTime.class))).thenReturn(Collections.emptyList());

            // When
            cancelOverdueConfirmedReservationService.execute();

            // Then
            verify(reservation, never()).cancel();
            verify(reservationRepository, never()).save(any());
            verify(penaltyRedisUtil, never()).applyPenalty(any());
        }
    }

    @Nested
    @DisplayName("만료된 CONFIRMED 예약이 있고 기기가 작동 중이면")
    class MachineRunning {

        @Test
        @DisplayName("자동으로 RUNNING 상태로 시작한다")
        void execute_ShouldAutoStart_WhenMachineRunning() {
            // Given
            when(reservationRepository.findExpiredConfirmedReservations(eq(ReservationStatus.CONFIRMED),
                    any(LocalDateTime.class))).thenReturn(List.of(reservation));
            when(reservation.getMachine()).thenReturn(machine);
            when(machine.getDeviceId()).thenReturn("device-123");
            when(detectMachineRunningService.execute("device-123")).thenReturn(true);

            var completionTimeAttr = new SmartThingsDeviceStatusResDto.AttributeState("2026-01-26T15:30:00Z",
                    "2026-01-26T14:30:00Z",
                    null);
            var washerOpState = new SmartThingsDeviceStatusResDto.WasherOperatingState(null, null, completionTimeAttr);
            var componentStatus = new SmartThingsDeviceStatusResDto.ComponentStatus(washerOpState, null, null);
            var deviceStatus = new SmartThingsDeviceStatusResDto(java.util.Map.of("main", componentStatus));
            when(queryDeviceStatusService.execute("device-123")).thenReturn(deviceStatus);

            // When
            cancelOverdueConfirmedReservationService.execute();

            // Then
            verify(reservation, times(1)).start(any(LocalDateTime.class));
            verify(reservationRepository, times(1)).save(reservation);
            verify(reservation, never()).cancel();
            verify(penaltyRedisUtil, never()).applyPenalty(any());
        }
    }

    @Nested
    @DisplayName("만료된 CONFIRMED 예약이 있고 기기가 작동 중이지 않으면")
    class MachineNotRunning {

        @Test
        @DisplayName("DB 재조회 후 여전히 CONFIRMED이면 취소하고 패널티를 부여한다")
        void execute_ShouldCancelAndPenalize_WhenStillConfirmedAfterRefresh() {
            // Given
            when(reservationRepository.findExpiredConfirmedReservations(eq(ReservationStatus.CONFIRMED),
                    any(LocalDateTime.class))).thenReturn(List.of(reservation));
            when(reservation.getMachine()).thenReturn(machine);
            when(machine.getDeviceId()).thenReturn("device-123");
            when(detectMachineRunningService.execute("device-123")).thenReturn(false);
            when(reservation.isConfirmed()).thenReturn(true); // refresh 후에도 CONFIRMED
            when(reservation.getUser()).thenReturn(user);

            // When
            cancelOverdueConfirmedReservationService.execute();

            // Then
            verify(entityManager, times(1)).refresh(reservation);
            verify(reservation, times(1)).cancel();
            verify(reservationRepository, times(1)).save(reservation);
            verify(penaltyRedisUtil, times(1)).applyPenalty(user);
            verify(reservation, never()).start(any());
        }

        @Test
        @DisplayName("DB 재조회 후 이미 RUNNING으로 전환됐으면 취소하지 않는다 (경합 조건)")
        void execute_ShouldSkipCancel_WhenAlreadyTransitionedToRunning() {
            // Given
            when(reservationRepository.findExpiredConfirmedReservations(eq(ReservationStatus.CONFIRMED),
                    any(LocalDateTime.class))).thenReturn(List.of(reservation));
            when(reservation.getMachine()).thenReturn(machine);
            when(machine.getDeviceId()).thenReturn("device-123");
            when(detectMachineRunningService.execute("device-123")).thenReturn(false);
            when(reservation.isConfirmed()).thenReturn(false); // refresh 후 RUNNING 등 다른 상태
            when(reservation.getStatus()).thenReturn(ReservationStatus.RUNNING);

            // When
            cancelOverdueConfirmedReservationService.execute();

            // Then
            verify(entityManager, times(1)).refresh(reservation);
            verify(reservation, never()).cancel();
            verify(reservationRepository, never()).save(any());
            verify(penaltyRedisUtil, never()).applyPenalty(any());
        }
    }
}
