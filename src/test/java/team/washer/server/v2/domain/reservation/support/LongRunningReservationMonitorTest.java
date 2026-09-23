package team.washer.server.v2.domain.reservation.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import team.washer.server.v2.domain.reservation.service.impl.ReservationLifecycleProcessor.RunningTarget;
import team.washer.server.v2.global.common.constants.ReservationConstants;

@DisplayName("LongRunningReservationMonitor 장기 실행 식별")
class LongRunningReservationMonitorTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 22, 12, 0);

    private LongRunningReservationMonitor monitor;

    @BeforeEach
    void setUp() {
        monitor = new LongRunningReservationMonitor();
    }

    private RunningTarget buildLongRunning(Long reservationId) {
        return new RunningTarget(reservationId, "device-1", "W-2F-L1", NOW.minusHours(3), NOW.minusHours(1), true);
    }

    @SuppressWarnings("unchecked")
    private Map<Long, LocalDateTime> lastReportedAt() {
        return (Map<Long, LocalDateTime>) ReflectionTestUtils.getField(monitor, "lastReportedAt");
    }

    @Nested
    @DisplayName("연속 조회 실패 추적")
    class QueryFailureTest {

        @Test
        @DisplayName("조회 실패가 이어지면 연속 실패 횟수가 증가한다")
        void shouldIncrement_WhenQueryFailsRepeatedly() {
            // When
            monitor.recordQueryFailure(1L);
            var failures = monitor.recordQueryFailure(1L);

            // Then
            assertThat(failures).isEqualTo(2);
        }

        @Test
        @DisplayName("조회에 성공하면 연속 실패 횟수가 초기화된다")
        void shouldReset_WhenQuerySucceeds() {
            // Given
            monitor.recordQueryFailure(1L);
            monitor.recordQueryFailure(1L);

            // When
            monitor.recordQuerySuccess(1L);
            var failures = monitor.recordQueryFailure(1L);

            // Then
            assertThat(failures).isEqualTo(1);
        }

        @Test
        @DisplayName("더 이상 RUNNING이 아닌 예약의 추적 값은 제거된다")
        void shouldDropTracking_WhenReservationNoLongerRunning() {
            // Given
            monitor.recordQueryFailure(1L);
            monitor.recordQueryFailure(2L);
            monitor.report(List.of(buildLongRunning(1L)), NOW);

            // When
            monitor.retainOnly(List.of(2L));

            // Then
            assertThat(monitor.recordQueryFailure(1L)).isEqualTo(1);
            assertThat(monitor.recordQueryFailure(2L)).isEqualTo(2);
            assertThat(lastReportedAt()).doesNotContainKey(1L);
        }
    }

    @Nested
    @DisplayName("장기 실행 보고")
    class ReportTest {

        @Test
        @DisplayName("처음 감지된 장기 실행 예약은 보고한다")
        void shouldReport_WhenFirstDetected() {
            // When
            monitor.report(List.of(buildLongRunning(1L)), NOW);

            // Then
            assertThat(lastReportedAt()).containsEntry(1L, NOW);
        }

        @Test
        @DisplayName("보고 간격이 지나기 전에는 같은 예약을 다시 보고하지 않는다")
        void shouldNotReportAgain_WithinInterval() {
            // Given
            monitor.report(List.of(buildLongRunning(1L)), NOW);
            var beforeInterval = NOW.plusMinutes(ReservationConstants.LONG_RUNNING_REPORT_INTERVAL_MINUTES - 1);

            // When
            monitor.report(List.of(buildLongRunning(1L)), beforeInterval);

            // Then
            assertThat(lastReportedAt()).containsEntry(1L, NOW);
        }

        @Test
        @DisplayName("보고 간격이 지나면 같은 예약을 다시 보고한다")
        void shouldReportAgain_AfterInterval() {
            // Given
            monitor.report(List.of(buildLongRunning(1L)), NOW);
            var afterInterval = NOW.plusMinutes(ReservationConstants.LONG_RUNNING_REPORT_INTERVAL_MINUTES);

            // When
            monitor.report(List.of(buildLongRunning(1L)), afterInterval);

            // Then
            assertThat(lastReportedAt()).containsEntry(1L, afterInterval);
        }
    }
}
