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

import team.washer.server.v2.domain.machine.entity.Machine;
import team.washer.server.v2.domain.machine.repository.MachineRepository;
import team.washer.server.v2.domain.notification.support.ReservationNotificationSupport;
import team.washer.server.v2.domain.reservation.entity.Reservation;
import team.washer.server.v2.domain.reservation.enums.ReservationStatus;
import team.washer.server.v2.domain.reservation.repository.ReservationRepository;
import team.washer.server.v2.domain.reservation.service.impl.CancelOverdueReservationServiceImpl;
import team.washer.server.v2.domain.reservation.util.PenaltyRedisUtil;
import team.washer.server.v2.domain.smartthings.dto.response.SmartThingsDeviceStatusResDto;
import team.washer.server.v2.domain.smartthings.support.DeviceStatusQuerySupport;
import team.washer.server.v2.domain.smartthings.support.MachineStateDetectionSupport;
import team.washer.server.v2.domain.user.entity.User;

@ExtendWith(MockitoExtension.class)
class CancelOverdueReservationServiceTest {

    @InjectMocks
    private CancelOverdueReservationServiceImpl cancelOverdueReservationService;

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private MachineRepository machineRepository;

    @Mock
    private PenaltyRedisUtil penaltyRedisUtil;

    @Mock
    private ReservationNotificationSupport reservationNotificationSupport;

    @Mock
    private MachineStateDetectionSupport machineStateDetectionSupport;

    @Mock
    private DeviceStatusQuerySupport deviceStatusQuerySupport;

    @Mock
    private Reservation reservation;

    @Mock
    private Machine machine;

    @Mock
    private User user;

    @Nested
    @DisplayName("만료된 예약이 없으면")
    class NoExpiredReservations {

        @Test
        @DisplayName("아무 동작도 하지 않는다")
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
            verify(penaltyRedisUtil, never()).applyCooldown(any());
        }
    }

    @Nested
    @DisplayName("만료된 RESERVED 예약이 있고 기기가 작동 중이면")
    class MachineRunning {

        @Test
        @DisplayName("자동으로 RUNNING 상태로 시작하고 시작 알림을 전송한다")
        void execute_ShouldAutoStartAndNotify_WhenMachineRunning() {
            // Given
            when(reservationRepository.findExpiredReservations(eq(ReservationStatus.RESERVED),
                    any(LocalDateTime.class),
                    any(LocalDateTime.class))).thenReturn(List.of(reservation));
            when(reservation.getMachine()).thenReturn(machine);
            when(reservation.getUser()).thenReturn(user);
            when(machine.getDeviceId()).thenReturn("device-123");
            when(machineStateDetectionSupport.isRunning("device-123")).thenReturn(true);

            var completionTimeAttr = new SmartThingsDeviceStatusResDto.AttributeState("2026-01-26T15:30:00Z",
                    "2026-01-26T14:30:00Z",
                    null);
            var washerOpState = new SmartThingsDeviceStatusResDto.WasherOperatingState(null, null, completionTimeAttr);
            var componentStatus = new SmartThingsDeviceStatusResDto.ComponentStatus(washerOpState, null, null);
            var deviceStatus = new SmartThingsDeviceStatusResDto(java.util.Map.of("main", componentStatus));
            when(deviceStatusQuerySupport.queryDeviceStatus("device-123")).thenReturn(deviceStatus);

            // When
            cancelOverdueReservationService.execute();

            // Then
            verify(reservation, times(1)).start(any(LocalDateTime.class));
            verify(reservationRepository, times(1)).save(reservation);
            verify(reservationNotificationSupport, times(1))
                    .sendStarted(eq(user), eq(machine), any(LocalDateTime.class));
            verify(reservation, never()).cancel();
            verify(penaltyRedisUtil, never()).applyCooldown(any());
        }
    }

    @Nested
    @DisplayName("만료된 RESERVED 예약이 있고 기기가 작동 중이지 않으면")
    class MachineNotRunning {

        @Test
        @DisplayName("예약을 취소하고 타임아웃 패널티를 부여한다 (첫 경고)")
        void execute_ShouldCancelAndApplyTimeoutPenalty_WhenFirstWarning() {
            // Given
            when(reservationRepository.findExpiredReservations(eq(ReservationStatus.RESERVED),
                    any(LocalDateTime.class),
                    any(LocalDateTime.class))).thenReturn(List.of(reservation));
            when(reservation.getMachine()).thenReturn(machine);
            when(machine.getDeviceId()).thenReturn("device-123");
            when(machineStateDetectionSupport.isRunning("device-123")).thenReturn(false);
            when(reservation.getUser()).thenReturn(user);
            when(user.getId()).thenReturn(1L);
            when(penaltyRedisUtil.hasWarning(1L)).thenReturn(false);
            when(penaltyRedisUtil.getCancellationCount(1L)).thenReturn(1L);

            // When
            cancelOverdueReservationService.execute();

            // Then
            verify(reservation, times(1)).cancel();
            verify(reservationRepository, times(1)).save(reservation);
            verify(penaltyRedisUtil, times(1)).applyCooldown(1L);
            verify(penaltyRedisUtil, times(1)).recordCancellation(1L);
            verify(penaltyRedisUtil, times(1)).applyWarning(1L);
            verify(reservationNotificationSupport, times(1)).sendTimeoutWarning(user, machine);
            verify(reservationNotificationSupport, never()).sendAutoCancellation(any(), any());
            verify(reservation, never()).start(any());
        }

        @Test
        @DisplayName("이미 경고가 있으면 자동 취소 알림을 전송한다")
        void execute_ShouldSendAutoCancellation_WhenWarningAlreadyExists() {
            // Given
            when(reservationRepository.findExpiredReservations(eq(ReservationStatus.RESERVED),
                    any(LocalDateTime.class),
                    any(LocalDateTime.class))).thenReturn(List.of(reservation));
            when(reservation.getMachine()).thenReturn(machine);
            when(machine.getDeviceId()).thenReturn("device-123");
            when(machineStateDetectionSupport.isRunning("device-123")).thenReturn(false);
            when(reservation.getUser()).thenReturn(user);
            when(user.getId()).thenReturn(1L);
            when(penaltyRedisUtil.hasWarning(1L)).thenReturn(true);
            when(penaltyRedisUtil.getCancellationCount(1L)).thenReturn(2L);

            // When
            cancelOverdueReservationService.execute();

            // Then
            verify(penaltyRedisUtil, times(1)).applyCooldown(1L);
            verify(penaltyRedisUtil, times(1)).recordCancellation(1L);
            verify(penaltyRedisUtil, never()).applyWarning(any());
            verify(reservationNotificationSupport, times(1)).sendAutoCancellation(user, machine);
            verify(reservationNotificationSupport, never()).sendTimeoutWarning(any(), any());
        }

        @Test
        @DisplayName("48시간 내 취소 횟수가 4회를 초과하면 48시간 예약 차단을 적용한다")
        void execute_ShouldApplyBlock_WhenCancellationCountExceedsLimit() {
            // Given
            when(reservationRepository.findExpiredReservations(eq(ReservationStatus.RESERVED),
                    any(LocalDateTime.class),
                    any(LocalDateTime.class))).thenReturn(List.of(reservation));
            when(reservation.getMachine()).thenReturn(machine);
            when(machine.getDeviceId()).thenReturn("device-123");
            when(machineStateDetectionSupport.isRunning("device-123")).thenReturn(false);
            when(reservation.getUser()).thenReturn(user);
            when(user.getId()).thenReturn(1L);
            when(user.getRoomNumber()).thenReturn("101");
            when(penaltyRedisUtil.hasWarning(1L)).thenReturn(true);
            when(penaltyRedisUtil.getCancellationCount(1L)).thenReturn(5L);

            // When
            cancelOverdueReservationService.execute();

            // Then
            verify(penaltyRedisUtil, times(1)).applyBlock("101");
        }
    }
}
