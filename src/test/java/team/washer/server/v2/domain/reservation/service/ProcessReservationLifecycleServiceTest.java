package team.washer.server.v2.domain.reservation.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import team.washer.server.v2.domain.reservation.enums.ReservationStatus;
import team.washer.server.v2.domain.reservation.service.impl.ProcessReservationLifecycleServiceImpl;
import team.washer.server.v2.domain.reservation.service.impl.ReservationLifecycleProcessor;
import team.washer.server.v2.domain.reservation.service.impl.ReservationLifecycleProcessor.LifecycleTarget;
import team.washer.server.v2.domain.smartthings.dto.response.SmartThingsDeviceStatusResDto;
import team.washer.server.v2.domain.smartthings.support.DeviceStatusQuerySupport;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProcessReservationLifecycleService 조율")
class ProcessReservationLifecycleServiceTest {

    @InjectMocks
    private ProcessReservationLifecycleServiceImpl processReservationLifecycleService;

    @Mock
    private ReservationLifecycleProcessor reservationLifecycleProcessor;

    @Mock
    private DeviceStatusQuerySupport deviceStatusQuerySupport;

    private SmartThingsDeviceStatusResDto buildDeviceStatus(String completionTime) {
        var completionTimeAttr = new SmartThingsDeviceStatusResDto.AttributeState(completionTime, null, null);
        var washerOpState = new SmartThingsDeviceStatusResDto.WasherOperatingState(null, null, completionTimeAttr);
        var componentStatus = new SmartThingsDeviceStatusResDto.ComponentStatus(washerOpState, null, null, null);
        return new SmartThingsDeviceStatusResDto(Map.of("main", componentStatus));
    }

    @Test
    @DisplayName("대상별로 트랜잭션 밖에서 기기 상태를 조회한 뒤 프로세서에 위임한다")
    void execute_ShouldQueryStatusOutsideTransactionThenDelegate() {
        // Given
        var reservedStatus = buildDeviceStatus("2026-01-26T15:30:00Z");
        var runningStatus = buildDeviceStatus("2026-01-26T16:00:00Z");
        when(reservationLifecycleProcessor.findTargets(ReservationStatus.RESERVED))
                .thenReturn(List.of(new LifecycleTarget(1L, "device-1")));
        when(reservationLifecycleProcessor.findTargets(ReservationStatus.RUNNING))
                .thenReturn(List.of(new LifecycleTarget(2L, "device-2")));
        when(deviceStatusQuerySupport.queryDeviceStatus("device-1")).thenReturn(reservedStatus);
        when(deviceStatusQuerySupport.queryDeviceStatus("device-2")).thenReturn(runningStatus);

        // When
        processReservationLifecycleService.execute();

        // Then
        verify(reservationLifecycleProcessor, times(1)).processReservedToRunning(1L, reservedStatus);
        verify(reservationLifecycleProcessor, times(1)).processRunningToCompleted(2L, runningStatus);
    }

    @Test
    @DisplayName("기기 상태 조회가 실패하면 해당 예약은 건너뛰고 처리를 계속한다")
    void execute_ShouldSkipReservation_WhenStatusQueryFails() {
        // Given
        when(reservationLifecycleProcessor.findTargets(ReservationStatus.RESERVED))
                .thenReturn(List.of(new LifecycleTarget(1L, "device-1")));
        when(reservationLifecycleProcessor.findTargets(ReservationStatus.RUNNING)).thenReturn(List.of());
        when(deviceStatusQuerySupport.queryDeviceStatus("device-1")).thenThrow(new RuntimeException("api error"));

        // When
        processReservationLifecycleService.execute();

        // Then
        verify(reservationLifecycleProcessor, never()).processReservedToRunning(anyLong(),
                any(SmartThingsDeviceStatusResDto.class));
    }

    @Test
    @DisplayName("처리 대상이 없으면 기기 상태를 조회하지 않는다")
    void execute_ShouldNotQueryStatus_WhenNoTargets() {
        // Given
        when(reservationLifecycleProcessor.findTargets(ReservationStatus.RESERVED)).thenReturn(List.of());
        when(reservationLifecycleProcessor.findTargets(ReservationStatus.RUNNING)).thenReturn(List.of());

        // When
        processReservationLifecycleService.execute();

        // Then
        verify(deviceStatusQuerySupport, never()).queryDeviceStatus(eq("device-1"));
        verify(reservationLifecycleProcessor, never()).processReservedToRunning(anyLong(),
                any(SmartThingsDeviceStatusResDto.class));
        verify(reservationLifecycleProcessor, never()).processRunningToCompleted(anyLong(),
                any(SmartThingsDeviceStatusResDto.class));
    }
}
