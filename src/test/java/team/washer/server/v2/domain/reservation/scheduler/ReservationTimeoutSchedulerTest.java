package team.washer.server.v2.domain.reservation.scheduler;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
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
import team.washer.server.v2.global.thirdparty.smartthings.SmartThingsOperationTimePolicy;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReservationTimeoutScheduler 예약 타임아웃 스케줄러")
class ReservationTimeoutSchedulerTest {

    @InjectMocks
    private ReservationTimeoutScheduler reservationTimeoutScheduler;

    @Mock
    private CancelOverdueReservationService cancelOverdueReservationService;

    @Mock
    private SmartThingsOperationTimePolicy operationTimePolicy;

    @Nested
    @DisplayName("운영 시간 확인")
    class OperationTimeTest {

        @Test
        @DisplayName("운영 시간 내이면 타임아웃 취소를 수행한다")
        void shouldExecute_WhenWithinOperationHours() {
            // Given
            when(operationTimePolicy.isOperationAllowed()).thenReturn(true);

            // When
            reservationTimeoutScheduler.checkReservationTimeouts();

            // Then
            verify(cancelOverdueReservationService, times(1)).execute();
        }

        @Test
        @DisplayName("운영 시간 외이면 타임아웃 취소를 수행하지 않는다")
        void shouldSkip_WhenOutsideOperationHours() {
            // Given
            when(operationTimePolicy.isOperationAllowed()).thenReturn(false);

            // When
            reservationTimeoutScheduler.checkReservationTimeouts();

            // Then
            verify(cancelOverdueReservationService, never()).execute();
        }
    }

    @Nested
    @DisplayName("예외 처리")
    class ExceptionHandlingTest {

        @Test
        @DisplayName("타임아웃 취소가 실패해도 예외를 전파하지 않는다")
        void shouldNotPropagate_WhenExecutionFails() {
            // Given
            when(operationTimePolicy.isOperationAllowed()).thenReturn(true);
            doThrow(new IllegalStateException("타임아웃 취소 실패")).when(cancelOverdueReservationService).execute();

            // When & Then
            assertThatCode(() -> reservationTimeoutScheduler.checkReservationTimeouts()).doesNotThrowAnyException();
        }
    }
}
