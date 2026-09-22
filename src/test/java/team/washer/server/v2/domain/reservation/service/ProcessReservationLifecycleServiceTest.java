package team.washer.server.v2.domain.reservation.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import team.washer.server.v2.domain.reservation.enums.ReservationStatus;
import team.washer.server.v2.domain.reservation.service.impl.ProcessReservationLifecycleServiceImpl;
import team.washer.server.v2.domain.reservation.service.impl.ReservationLifecycleProcessor;
import team.washer.server.v2.domain.reservation.service.impl.ReservationLifecycleProcessor.CompletedMachine;
import team.washer.server.v2.domain.reservation.service.impl.ReservationLifecycleProcessor.LifecycleTarget;
import team.washer.server.v2.domain.reservation.service.impl.ReservationLifecycleProcessor.LongRunningReservation;
import team.washer.server.v2.domain.reservation.support.LongRunningReservationMonitor;
import team.washer.server.v2.domain.smartthings.dto.response.SmartThingsDeviceStatusResDto;
import team.washer.server.v2.domain.smartthings.exception.SmartThingsPermissionException;
import team.washer.server.v2.domain.smartthings.support.DeviceShutdownSupport;
import team.washer.server.v2.domain.smartthings.support.DeviceStatusQuerySupport;
import team.washer.server.v2.global.thirdparty.discord.service.DiscordErrorNotificationService;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProcessReservationLifecycleService 조율")
class ProcessReservationLifecycleServiceTest {

    @InjectMocks
    private ProcessReservationLifecycleServiceImpl processReservationLifecycleService;

    @Mock
    private ReservationLifecycleProcessor reservationLifecycleProcessor;

    @Mock
    private DeviceStatusQuerySupport deviceStatusQuerySupport;

    @Mock
    private DeviceShutdownSupport deviceShutdownSupport;

    @Mock
    private DiscordErrorNotificationService discordErrorNotificationService;

    @Mock
    private LongRunningReservationMonitor longRunningReservationMonitor;

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
        verify(deviceShutdownSupport, never()).shutdownAfterCompletion(any(), any(), anyBoolean(), any());
    }

    @Test
    @DisplayName("예약이 완료되면 트랜잭션 밖에서 완료 판정에 쓴 상태로 기기 전원 차단을 요청한다")
    void execute_ShouldShutdownMachine_WhenReservationCompleted() {
        // Given
        var runningStatus = buildDeviceStatus("2026-01-26T16:00:00Z");
        when(reservationLifecycleProcessor.findTargets(ReservationStatus.RESERVED)).thenReturn(List.of());
        when(reservationLifecycleProcessor.findTargets(ReservationStatus.RUNNING))
                .thenReturn(List.of(new LifecycleTarget(2L, "device-2")));
        when(deviceStatusQuerySupport.queryDeviceStatus("device-2")).thenReturn(runningStatus);
        when(reservationLifecycleProcessor.processRunningToCompleted(2L, runningStatus))
                .thenReturn(Optional.of(new CompletedMachine("D-2F-L1", "device-2", false)));

        // When
        processReservationLifecycleService.execute();

        // Then
        verify(deviceShutdownSupport, times(1)).shutdownAfterCompletion("D-2F-L1", "device-2", false, runningStatus);
    }

    @Test
    @DisplayName("완료 후 전원 차단이 SmartThings 권한 오류로 실패하면 Discord로 알리고 남은 예약 처리를 중단한다")
    void execute_ShouldNotifyAndStop_WhenShutdownRejectedByPermission() {
        // Given
        var firstStatus = buildDeviceStatus("2026-01-26T16:00:00Z");
        when(reservationLifecycleProcessor.findTargets(ReservationStatus.RESERVED)).thenReturn(List.of());
        when(reservationLifecycleProcessor.findTargets(ReservationStatus.RUNNING))
                .thenReturn(List.of(new LifecycleTarget(2L, "device-2"), new LifecycleTarget(3L, "device-3")));
        when(deviceStatusQuerySupport.queryDeviceStatus("device-2")).thenReturn(firstStatus);
        when(reservationLifecycleProcessor.processRunningToCompleted(2L, firstStatus))
                .thenReturn(Optional.of(new CompletedMachine("D-2F-L1", "device-2", false)));
        when(deviceShutdownSupport.shutdownAfterCompletion("D-2F-L1", "device-2", false, firstStatus))
                .thenThrow(new SmartThingsPermissionException("권한 없음"));
        ReflectionTestUtils.setField(processReservationLifecycleService,
                "discordErrorNotificationService",
                discordErrorNotificationService);

        // When
        processReservationLifecycleService.execute();

        // Then
        verify(discordErrorNotificationService, times(1))
                .notifyError(any(SmartThingsPermissionException.class), eq("예약 완료 기기 종료 - SmartThings 권한 오류"), any());
        verify(deviceStatusQuerySupport, never()).queryDeviceStatus("device-3");
        verify(reservationLifecycleProcessor, never()).processRunningToCompleted(eq(3L), any());
    }

    @Test
    @DisplayName("완료 후 전원 차단이 실패해도 다음 예약 처리를 계속한다")
    void execute_ShouldContinue_WhenShutdownAfterCompletionFails() {
        // Given
        var firstStatus = buildDeviceStatus("2026-01-26T16:00:00Z");
        var secondStatus = buildDeviceStatus("2026-01-26T16:10:00Z");
        when(reservationLifecycleProcessor.findTargets(ReservationStatus.RESERVED)).thenReturn(List.of());
        when(reservationLifecycleProcessor.findTargets(ReservationStatus.RUNNING))
                .thenReturn(List.of(new LifecycleTarget(2L, "device-2"), new LifecycleTarget(3L, "device-3")));
        when(deviceStatusQuerySupport.queryDeviceStatus("device-2")).thenReturn(firstStatus);
        when(deviceStatusQuerySupport.queryDeviceStatus("device-3")).thenReturn(secondStatus);
        when(reservationLifecycleProcessor.processRunningToCompleted(2L, firstStatus))
                .thenReturn(Optional.of(new CompletedMachine("D-2F-L1", "device-2", false)));
        when(deviceShutdownSupport.shutdownAfterCompletion("D-2F-L1", "device-2", false, firstStatus))
                .thenThrow(new RuntimeException("api error"));

        // When
        processReservationLifecycleService.execute();

        // Then
        verify(reservationLifecycleProcessor, times(1)).processRunningToCompleted(3L, secondStatus);
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

    @Test
    @DisplayName("RUNNING 예약의 기기 상태 조회가 실패하면 연속 실패를 기록하고 다음 예약 처리를 계속한다")
    void execute_ShouldRecordFailureAndContinue_WhenRunningStatusQueryFails() {
        // Given
        var secondStatus = buildDeviceStatus("2026-01-26T16:10:00Z");
        when(reservationLifecycleProcessor.findTargets(ReservationStatus.RESERVED)).thenReturn(List.of());
        when(reservationLifecycleProcessor.findTargets(ReservationStatus.RUNNING))
                .thenReturn(List.of(new LifecycleTarget(2L, "device-2"), new LifecycleTarget(3L, "device-3")));
        when(deviceStatusQuerySupport.queryDeviceStatus("device-2")).thenThrow(new RuntimeException("timeout"));
        when(deviceStatusQuerySupport.queryDeviceStatus("device-3")).thenReturn(secondStatus);

        // When
        processReservationLifecycleService.execute();

        // Then
        verify(longRunningReservationMonitor, times(1)).retainOnly(List.of(2L, 3L));
        verify(longRunningReservationMonitor, times(1)).recordQueryFailure(2L);
        verify(longRunningReservationMonitor, times(1)).recordQuerySuccess(3L);
        verify(reservationLifecycleProcessor, never()).processRunningToCompleted(eq(2L), any());
        verify(reservationLifecycleProcessor, times(1)).processRunningToCompleted(3L, secondStatus);
    }

    @Test
    @DisplayName("주기 끝에 장기 실행 예약을 조회해 보고하며 예약 상태는 바꾸지 않는다")
    void execute_ShouldReportLongRunningReservations() {
        // Given
        var longRunning = new LongRunningReservation(2L,
                "W-2F-L1",
                "device-2",
                LocalDateTime.of(2026, 9, 22, 9, 0),
                LocalDateTime.of(2026, 9, 22, 10, 0));
        when(reservationLifecycleProcessor.findTargets(ReservationStatus.RESERVED)).thenReturn(List.of());
        when(reservationLifecycleProcessor.findTargets(ReservationStatus.RUNNING)).thenReturn(List.of());
        when(reservationLifecycleProcessor.findLongRunningReservations()).thenReturn(List.of(longRunning));

        // When
        processReservationLifecycleService.execute();

        // Then
        verify(longRunningReservationMonitor, times(1)).report(eq(List.of(longRunning)), any(LocalDateTime.class));
        verify(reservationLifecycleProcessor, never()).processRunningToCompleted(anyLong(), any());
    }
}
