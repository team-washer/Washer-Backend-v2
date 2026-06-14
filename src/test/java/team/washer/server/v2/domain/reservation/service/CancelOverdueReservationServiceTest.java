package team.washer.server.v2.domain.reservation.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
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

import team.washer.server.v2.domain.reservation.service.impl.CancelOverdueReservationServiceImpl;
import team.washer.server.v2.domain.reservation.service.impl.OverdueReservationProcessor;
import team.washer.server.v2.domain.reservation.service.impl.OverdueReservationProcessor.OverdueResult;
import team.washer.server.v2.domain.reservation.service.impl.OverdueReservationProcessor.OverdueTarget;
import team.washer.server.v2.domain.smartthings.dto.response.SmartThingsDeviceStatusResDto;
import team.washer.server.v2.domain.smartthings.support.DeviceStatusQuerySupport;

@ExtendWith(MockitoExtension.class)
@DisplayName("CancelOverdueReservationService 조율")
class CancelOverdueReservationServiceTest {

    @InjectMocks
    private CancelOverdueReservationServiceImpl cancelOverdueReservationService;

    @Mock
    private OverdueReservationProcessor overdueReservationProcessor;

    @Mock
    private DeviceStatusQuerySupport deviceStatusQuerySupport;

    private SmartThingsDeviceStatusResDto buildDeviceStatus(String completionTime) {
        var completionTimeAttr = new SmartThingsDeviceStatusResDto.AttributeState(completionTime, null, null);
        var washerOpState = new SmartThingsDeviceStatusResDto.WasherOperatingState(null, null, completionTimeAttr);
        var componentStatus = new SmartThingsDeviceStatusResDto.ComponentStatus(washerOpState, null, null, null);
        return new SmartThingsDeviceStatusResDto(Map.of("main", componentStatus));
    }

    @Test
    @DisplayName("만료된 예약이 없으면 기기 상태를 조회하지 않고 종료한다")
    void execute_ShouldDoNothing_WhenNoExpiredTargets() {
        // Given
        when(overdueReservationProcessor.findExpiredTargets()).thenReturn(List.of());

        // When
        cancelOverdueReservationService.execute();

        // Then
        verify(deviceStatusQuerySupport, never()).queryDeviceStatus(any());
        verify(overdueReservationProcessor, never()).processOverdue(anyLong(),
                any(SmartThingsDeviceStatusResDto.class));
    }

    @Test
    @DisplayName("대상별로 트랜잭션 밖에서 기기 상태를 조회한 뒤 프로세서에 위임한다")
    void execute_ShouldQueryStatusOutsideTransactionThenDelegate() {
        // Given
        var status = buildDeviceStatus("2026-01-26T15:30:00Z");
        when(overdueReservationProcessor.findExpiredTargets()).thenReturn(List.of(new OverdueTarget(1L, "device-1")));
        when(deviceStatusQuerySupport.queryDeviceStatus("device-1")).thenReturn(status);
        when(overdueReservationProcessor.processOverdue(1L, status)).thenReturn(OverdueResult.CANCELLED);

        // When
        cancelOverdueReservationService.execute();

        // Then
        verify(overdueReservationProcessor, times(1)).processOverdue(1L, status);
    }

    @Test
    @DisplayName("기기 상태 조회가 실패하면 해당 예약은 건너뛰고 처리를 계속한다")
    void execute_ShouldSkipReservation_WhenStatusQueryFails() {
        // Given
        when(overdueReservationProcessor.findExpiredTargets()).thenReturn(List.of(new OverdueTarget(1L, "device-1")));
        when(deviceStatusQuerySupport.queryDeviceStatus("device-1")).thenThrow(new RuntimeException("api error"));

        // When
        cancelOverdueReservationService.execute();

        // Then
        verify(overdueReservationProcessor, never()).processOverdue(anyLong(),
                any(SmartThingsDeviceStatusResDto.class));
    }
}
