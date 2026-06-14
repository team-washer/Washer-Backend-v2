package team.washer.server.v2.domain.reservation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
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
import team.washer.server.v2.domain.reservation.service.impl.OverdueReservationProcessor;
import team.washer.server.v2.domain.reservation.service.impl.OverdueReservationProcessor.OverdueResult;
import team.washer.server.v2.domain.reservation.util.PenaltyRedisUtil;
import team.washer.server.v2.domain.smartthings.dto.response.SmartThingsDeviceStatusResDto;
import team.washer.server.v2.domain.smartthings.support.MachineStateDetectionSupport;
import team.washer.server.v2.domain.user.entity.User;

@ExtendWith(MockitoExtension.class)
@DisplayName("OverdueReservationProcessor 개별 만료 예약 처리")
class OverdueReservationProcessorTest {

    private static final Long RESERVATION_ID = 1L;

    @InjectMocks
    private OverdueReservationProcessor overdueReservationProcessor;

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

    private void givenReservedReservation() {
        when(reservationRepository.findById(RESERVATION_ID)).thenReturn(Optional.of(reservation));
        when(reservation.isReserved()).thenReturn(true);
        when(reservation.getMachine()).thenReturn(machine);
    }

    @Nested
    @DisplayName("기기가 작동 중이면")
    class MachineRunning {

        @Test
        @DisplayName("자동으로 RUNNING 상태로 시작하고 시작 알림을 전송하며 AUTO_STARTED를 반환한다")
        void shouldAutoStartAndNotify_WhenMachineRunning() {
            // Given
            var deviceStatus = buildDeviceStatus("2026-01-26T15:30:00Z");
            givenReservedReservation();
            when(reservation.getUser()).thenReturn(user);
            when(machineStateDetectionSupport.isRunning(any(SmartThingsDeviceStatusResDto.class), anyBoolean()))
                    .thenReturn(true);

            // When
            var result = overdueReservationProcessor.processOverdue(RESERVATION_ID, deviceStatus);

            // Then
            assertThat(result).isEqualTo(OverdueResult.AUTO_STARTED);
            verify(reservation, times(1)).start(any(LocalDateTime.class));
            verify(reservationRepository, times(1)).save(reservation);
            verify(reservationNotificationSupport, times(1))
                    .sendStarted(eq(user), eq(machine), any(LocalDateTime.class));
            verify(reservation, never()).cancel();
            verify(penaltyRedisUtil, never()).applyCooldown(any(), any());
        }
    }

    @Nested
    @DisplayName("기기가 작동 중이지 않으면")
    class MachineNotRunning {

        @Test
        @DisplayName("예약을 취소하고 타임아웃 패널티를 부여한다 (첫 경고) 후 CANCELLED를 반환한다")
        void shouldCancelAndApplyTimeoutPenalty_WhenFirstWarning() {
            // Given
            var deviceStatus = buildDeviceStatus(null);
            givenReservedReservation();
            when(machineStateDetectionSupport.isRunning(any(SmartThingsDeviceStatusResDto.class), anyBoolean()))
                    .thenReturn(false);
            when(reservation.getUser()).thenReturn(user);
            when(user.getId()).thenReturn(1L);
            when(penaltyRedisUtil.hasWarning(1L)).thenReturn(false);
            when(penaltyRedisUtil.getCancellationCount(1L)).thenReturn(1L);

            // When
            var result = overdueReservationProcessor.processOverdue(RESERVATION_ID, deviceStatus);

            // Then
            assertThat(result).isEqualTo(OverdueResult.CANCELLED);
            verify(reservation, times(1)).cancel();
            verify(reservationRepository, times(1)).save(reservation);
            verify(penaltyRedisUtil, times(1)).applyCooldown(eq(1L), any());
            verify(penaltyRedisUtil, times(1)).recordCancellation(1L);
            verify(penaltyRedisUtil, times(1)).applyWarning(1L);
            verify(reservationNotificationSupport, times(1)).sendTimeoutWarning(user, machine);
            verify(reservationNotificationSupport, never()).sendAutoCancellation(any(), any());
            verify(reservation, never()).start(any());
        }

        @Test
        @DisplayName("이미 경고가 있으면 자동 취소 알림을 전송한다")
        void shouldSendAutoCancellation_WhenWarningAlreadyExists() {
            // Given
            var deviceStatus = buildDeviceStatus(null);
            givenReservedReservation();
            when(machineStateDetectionSupport.isRunning(any(SmartThingsDeviceStatusResDto.class), anyBoolean()))
                    .thenReturn(false);
            when(reservation.getUser()).thenReturn(user);
            when(user.getId()).thenReturn(1L);
            when(penaltyRedisUtil.hasWarning(1L)).thenReturn(true);
            when(penaltyRedisUtil.getCancellationCount(1L)).thenReturn(2L);

            // When
            overdueReservationProcessor.processOverdue(RESERVATION_ID, deviceStatus);

            // Then
            verify(penaltyRedisUtil, times(1)).applyCooldown(eq(1L), any());
            verify(penaltyRedisUtil, times(1)).recordCancellation(1L);
            verify(penaltyRedisUtil, never()).applyWarning(any());
            verify(reservationNotificationSupport, times(1)).sendAutoCancellation(user, machine);
            verify(reservationNotificationSupport, never()).sendTimeoutWarning(any(), any());
        }

        @Test
        @DisplayName("48시간 내 취소 횟수가 한도를 초과하면 48시간 예약 차단을 적용한다")
        void shouldApplyBlock_WhenCancellationCountExceedsLimit() {
            // Given
            var deviceStatus = buildDeviceStatus(null);
            givenReservedReservation();
            when(machineStateDetectionSupport.isRunning(any(SmartThingsDeviceStatusResDto.class), anyBoolean()))
                    .thenReturn(false);
            when(reservation.getUser()).thenReturn(user);
            when(user.getId()).thenReturn(1L);
            when(user.getRoomNumber()).thenReturn("101");
            when(penaltyRedisUtil.hasWarning(1L)).thenReturn(true);
            when(penaltyRedisUtil.getCancellationCount(1L)).thenReturn(5L);

            // When
            overdueReservationProcessor.processOverdue(RESERVATION_ID, deviceStatus);

            // Then
            verify(penaltyRedisUtil, times(1)).applyBlock("101");
        }
    }

    @Nested
    @DisplayName("재조회 시점에 RESERVED 상태가 아니면")
    class NoLongerReserved {

        @Test
        @DisplayName("아무 처리도 하지 않고 SKIPPED를 반환한다")
        void shouldSkip_WhenNoLongerReserved() {
            // Given
            var deviceStatus = buildDeviceStatus(null);
            when(reservationRepository.findById(RESERVATION_ID)).thenReturn(Optional.of(reservation));
            when(reservation.isReserved()).thenReturn(false);

            // When
            var result = overdueReservationProcessor.processOverdue(RESERVATION_ID, deviceStatus);

            // Then
            assertThat(result).isEqualTo(OverdueResult.SKIPPED);
            verify(reservation, never()).start(any());
            verify(reservation, never()).cancel();
            verify(reservationRepository, never()).save(any());
        }
    }
}
