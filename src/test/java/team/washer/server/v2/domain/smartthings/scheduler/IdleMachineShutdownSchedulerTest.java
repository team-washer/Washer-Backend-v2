package team.washer.server.v2.domain.smartthings.scheduler;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import team.washer.server.v2.domain.reservation.service.CancelOverdueReservationService;
import team.washer.server.v2.domain.smartthings.service.ShutdownIdleMachinesService;
import team.washer.server.v2.global.thirdparty.smartthings.SmartThingsOperationTimePolicy;

@ExtendWith(MockitoExtension.class)
@DisplayName("IdleMachineShutdownScheduler 타임아웃 정리 후 전원 차단 스케줄러")
class IdleMachineShutdownSchedulerTest {

    @InjectMocks
    private IdleMachineShutdownScheduler idleMachineShutdownScheduler;

    @Mock
    private CancelOverdueReservationService cancelOverdueReservationService;

    @Mock
    private ShutdownIdleMachinesService shutdownIdleMachinesService;

    @Mock
    private SmartThingsOperationTimePolicy operationTimePolicy;

    @Nested
    @DisplayName("실행 순서")
    class ExecutionOrderTest {

        @Test
        @DisplayName("예약 타임아웃을 먼저 정리한 뒤 전원 차단을 수행한다")
        void shouldCancelOverdueBeforeShutdown() {
            // Given
            when(operationTimePolicy.isOperationAllowed()).thenReturn(true);

            // When
            idleMachineShutdownScheduler.shutdownIdleMachines();

            // Then
            var inOrder = inOrder(cancelOverdueReservationService, shutdownIdleMachinesService);
            inOrder.verify(cancelOverdueReservationService, times(1)).execute();
            inOrder.verify(shutdownIdleMachinesService, times(1)).execute();
        }
    }

    @Nested
    @DisplayName("운영 시간 확인")
    class OperationTimeTest {

        @Test
        @DisplayName("운영 시간 외이면 타임아웃 정리와 전원 차단을 모두 수행하지 않는다")
        void shouldSkip_WhenOutsideOperationHours() {
            // Given
            when(operationTimePolicy.isOperationAllowed()).thenReturn(false);

            // When
            idleMachineShutdownScheduler.shutdownIdleMachines();

            // Then
            verify(cancelOverdueReservationService, never()).execute();
            verify(shutdownIdleMachinesService, never()).execute();
        }
    }

    @Nested
    @DisplayName("예외 처리")
    class ExceptionHandlingTest {

        @Test
        @DisplayName("타임아웃 정리가 실패하면 예약 상태를 확정하지 못했으므로 전원을 차단하지 않는다")
        void shouldNotShutdown_WhenOverdueCancellationFails() {
            // Given
            when(operationTimePolicy.isOperationAllowed()).thenReturn(true);
            doThrow(new IllegalStateException("타임아웃 취소 실패")).when(cancelOverdueReservationService).execute();

            // When & Then
            assertThatCode(() -> idleMachineShutdownScheduler.shutdownIdleMachines()).doesNotThrowAnyException();
            verify(shutdownIdleMachinesService, never()).execute();
        }

        @Test
        @DisplayName("전원 차단이 실패해도 예외를 전파하지 않는다")
        void shouldNotPropagate_WhenShutdownFails() {
            // Given
            when(operationTimePolicy.isOperationAllowed()).thenReturn(true);
            doThrow(new IllegalStateException("전원 차단 실패")).when(shutdownIdleMachinesService).execute();

            // When & Then
            assertThatCode(() -> idleMachineShutdownScheduler.shutdownIdleMachines()).doesNotThrowAnyException();
        }
    }
}
