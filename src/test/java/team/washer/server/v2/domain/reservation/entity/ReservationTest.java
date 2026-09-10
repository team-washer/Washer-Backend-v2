package team.washer.server.v2.domain.reservation.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.time.ZoneId;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import team.washer.server.v2.domain.reservation.enums.ReservationStatus;
import team.washer.server.v2.global.common.constants.ReservationConstants;

@DisplayName("Reservation 예상 완료 시각 상한 검증")
class ReservationTest {

    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");

    private Reservation buildReservedReservation() {
        return Reservation.builder().reservedAt(LocalDateTime.now(KOREA_ZONE)).build();
    }

    private Reservation buildRunningReservation(LocalDateTime expectedCompletionTime) {
        var reservation = buildReservedReservation();
        reservation.start(expectedCompletionTime);
        return reservation;
    }

    @Nested
    @DisplayName("예약 시작 시 상한 검증")
    class StartTest {

        @Test
        @DisplayName("합리적인 범위의 완료 예정 시각은 그대로 저장한다")
        void shouldStoreExpectedCompletionTime_WhenWithinReasonableCycle() {
            // Given
            var expectedCompletionTime = LocalDateTime.now(KOREA_ZONE)
                    .plusMinutes(ReservationConstants.DEFAULT_RESERVATION_DURATION_MINUTES);
            var reservation = buildReservedReservation();

            // When
            reservation.start(expectedCompletionTime);

            // Then
            assertThat(reservation.getExpectedCompletionTime()).isEqualTo(expectedCompletionTime);
        }

        @Test
        @DisplayName("사이클 상한을 넘는 완료 예정 시각은 저장하지 않는다")
        void shouldNotStoreExpectedCompletionTime_WhenExceedingMaxCycle() {
            // Given
            var outlier = LocalDateTime.now(KOREA_ZONE)
                    .plusMinutes(ReservationConstants.MAX_REASONABLE_CYCLE_MINUTES + 10);
            var reservation = buildReservedReservation();

            // When
            reservation.start(outlier);

            // Then
            assertThat(reservation.getExpectedCompletionTime()).isNull();
        }

        @Test
        @DisplayName("시작 시각보다 이른 완료 예정 시각은 저장하지 않는다")
        void shouldNotStoreExpectedCompletionTime_WhenBeforeStartTime() {
            // Given
            var pastTime = LocalDateTime.now(KOREA_ZONE).minusMinutes(10);
            var reservation = buildReservedReservation();

            // When
            reservation.start(pastTime);

            // Then
            assertThat(reservation.getExpectedCompletionTime()).isNull();
        }
    }

    @Nested
    @DisplayName("진행 중 완료 예정 시각 갱신")
    class UpdateExpectedCompletionTimeTest {

        @Test
        @DisplayName("합리적인 범위의 값이면 갱신하고 true를 반환한다")
        void shouldUpdateAndReturnTrue_WhenWithinReasonableCycle() {
            // Given
            var initial = LocalDateTime.now(KOREA_ZONE).plusMinutes(60);
            var reservation = buildRunningReservation(initial);
            var updated = initial.plusMinutes(5);

            // When
            var result = reservation.updateExpectedCompletionTime(updated);

            // Then
            assertThat(result).isTrue();
            assertThat(reservation.getExpectedCompletionTime()).isEqualTo(updated);
        }

        @Test
        @DisplayName("사이클 상한을 넘는 값이면 갱신하지 않고 false를 반환한다")
        void shouldNotUpdateAndReturnFalse_WhenExceedingMaxCycle() {
            // Given
            var initial = LocalDateTime.now(KOREA_ZONE).plusMinutes(60);
            var reservation = buildRunningReservation(initial);
            var outlier = LocalDateTime.now(KOREA_ZONE)
                    .plusMinutes(ReservationConstants.MAX_REASONABLE_CYCLE_MINUTES + 10);

            // When
            var result = reservation.updateExpectedCompletionTime(outlier);

            // Then
            assertThat(result).isFalse();
            assertThat(reservation.getExpectedCompletionTime()).isEqualTo(initial);
        }

        @Test
        @DisplayName("시작 시각보다 이른 값이면 갱신하지 않고 false를 반환한다")
        void shouldNotUpdateAndReturnFalse_WhenBeforeStartTime() {
            // Given
            var initial = LocalDateTime.now(KOREA_ZONE).plusMinutes(60);
            var reservation = buildRunningReservation(initial);
            var pastTime = LocalDateTime.now(KOREA_ZONE).minusMinutes(10);

            // When
            var result = reservation.updateExpectedCompletionTime(pastTime);

            // Then
            assertThat(result).isFalse();
            assertThat(reservation.getExpectedCompletionTime()).isEqualTo(initial);
        }

        @Test
        @DisplayName("null이면 갱신하지 않고 false를 반환한다")
        void shouldNotUpdateAndReturnFalse_WhenNull() {
            // Given
            var initial = LocalDateTime.now(KOREA_ZONE).plusMinutes(60);
            var reservation = buildRunningReservation(initial);

            // When
            var result = reservation.updateExpectedCompletionTime(null);

            // Then
            assertThat(result).isFalse();
            assertThat(reservation.getExpectedCompletionTime()).isEqualTo(initial);
        }
    }

    @Nested
    @DisplayName("완료 디바운스 카운터")
    class CompletionCountTest {

        @Test
        @DisplayName("감지될 때마다 1씩 증가하고 초기화하면 0으로 돌아간다")
        void shouldIncrementAndClear() {
            // Given
            var reservation = buildReservedReservation();

            // When
            reservation.incrementCompletionCount();
            reservation.incrementCompletionCount();

            // Then
            assertThat(reservation.getCompletionCount()).isEqualTo(2);

            // When
            reservation.clearCompletionCount();

            // Then
            assertThat(reservation.getCompletionCount()).isZero();
        }
    }

    @Nested
    @DisplayName("현재 활성 예약 판정")
    class IsCurrentlyActiveTest {

        private Reservation buildReservation(ReservationStatus status, int reservedMinutesAgo) {
            return Reservation.builder().status(status)
                    .reservedAt(LocalDateTime.now(KOREA_ZONE).minusMinutes(reservedMinutesAgo)).build();
        }

        @Test
        @DisplayName("타임아웃 전 RESERVED 예약은 활성으로 판정한다")
        void shouldReturnTrue_WhenReservedAndNotExpired() {
            // Given
            var reservation = buildReservation(ReservationStatus.RESERVED, 1);

            // When & Then
            assertThat(reservation.isCurrentlyActive()).isTrue();
        }

        @Test
        @DisplayName("타임아웃이 지난 RESERVED 예약은 활성으로 판정하지 않는다")
        void shouldReturnFalse_WhenReservedAndExpired() {
            // Given
            var reservation = buildReservation(ReservationStatus.RESERVED,
                    ReservationStatus.RESERVED.getTimeoutMinutes() + 1);

            // When & Then
            assertThat(reservation.isCurrentlyActive()).isFalse();
        }

        @Test
        @DisplayName("RUNNING 예약은 예약 시각과 무관하게 활성으로 판정한다")
        void shouldReturnTrue_WhenRunning() {
            // Given
            var reservation = buildReservation(ReservationStatus.RUNNING, 120);

            // When & Then
            assertThat(reservation.isCurrentlyActive()).isTrue();
        }

        @Test
        @DisplayName("완료·취소된 예약은 활성으로 판정하지 않는다")
        void shouldReturnFalse_WhenCompletedOrCancelled() {
            // Given
            var completed = buildReservation(ReservationStatus.COMPLETED, 1);
            var cancelled = buildReservation(ReservationStatus.CANCELLED, 1);

            // When & Then
            assertThat(completed.isCurrentlyActive()).isFalse();
            assertThat(cancelled.isCurrentlyActive()).isFalse();
        }
    }
}
