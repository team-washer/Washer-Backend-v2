package team.washer.server.v2.domain.reservation.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import team.washer.server.v2.domain.reservation.entity.Reservation;
import team.washer.server.v2.domain.reservation.enums.ReservationStatus;
import team.washer.server.v2.global.util.DateTimeUtil;

@DisplayName("ActiveReservationSelector 활성 예약 선택 규칙")
class ActiveReservationSelectorTest {

    private Reservation createReservation(ReservationStatus status) {
        return Reservation.builder().reservedAt(DateTimeUtil.nowInKorea()).status(status).build();
    }

    private Reservation createExpiredReservedReservation() {
        return Reservation.builder().reservedAt(DateTimeUtil.nowInKorea().minusMinutes(6))
                .status(ReservationStatus.RESERVED).build();
    }

    @Nested
    @DisplayName("RUNNING 우선 규칙")
    class RunningPriorityTest {

        @Test
        @DisplayName("RUNNING과 RESERVED가 공존하면 RUNNING을 고른다")
        void shouldSelectRunning_WhenBothStatusesExist() {
            // Given
            var reserved = createReservation(ReservationStatus.RESERVED);
            var running = createReservation(ReservationStatus.RUNNING);

            // When
            var selected = ActiveReservationSelector.selectPrimary(List.of(reserved, running));

            // Then
            assertThat(selected).containsSame(running);
        }

        @Test
        @DisplayName("최근 생성된 RESERVED가 앞에 와도 RUNNING을 고른다")
        void shouldSelectRunning_WhenReservedIsMoreRecent() {
            // Given
            var recentReserved = createReservation(ReservationStatus.RESERVED);
            var olderRunning = createReservation(ReservationStatus.RUNNING);

            // When
            var selected = ActiveReservationSelector.selectPrimary(List.of(recentReserved, olderRunning));

            // Then
            assertThat(selected).containsSame(olderRunning);
        }

        @Test
        @DisplayName("RESERVED만 있으면 첫 번째 RESERVED를 고른다")
        void shouldSelectFirstReserved_WhenOnlyReservedExists() {
            // Given
            var first = createReservation(ReservationStatus.RESERVED);
            var second = createReservation(ReservationStatus.RESERVED);

            // When
            var selected = ActiveReservationSelector.selectPrimary(List.of(first, second));

            // Then
            assertThat(selected).containsSame(first);
        }

        @Test
        @DisplayName("만료된 RESERVED만 있으면 선택하지 않는다")
        void shouldReturnEmpty_WhenOnlyExpiredReservedExists() {
            // Given
            var expired = createExpiredReservedReservation();

            // When
            var selected = ActiveReservationSelector.selectPrimary(List.of(expired));

            // Then
            assertThat(selected).isEmpty();
        }

        @Test
        @DisplayName("RUNNING이 여럿이면 첫 번째 RUNNING을 고른다")
        void shouldSelectFirstRunning_WhenMultipleRunningExist() {
            // Given
            var first = createReservation(ReservationStatus.RUNNING);
            var second = createReservation(ReservationStatus.RUNNING);

            // When
            var selected = ActiveReservationSelector.selectPrimary(List.of(first, second));

            // Then
            assertThat(selected).containsSame(first);
        }
    }

    @Nested
    @DisplayName("활성 예약이 없는 경우")
    class EmptyInputTest {

        @Test
        @DisplayName("빈 목록이면 결과가 없다")
        void shouldReturnEmpty_WhenListIsEmpty() {
            // When
            var selected = ActiveReservationSelector.selectPrimary(List.of());

            // Then
            assertThat(selected).isEmpty();
        }

        @Test
        @DisplayName("null이면 결과가 없다")
        void shouldReturnEmpty_WhenListIsNull() {
            // When
            var selected = ActiveReservationSelector.selectPrimary(null);

            // Then
            assertThat(selected).isEmpty();
        }
    }
}
