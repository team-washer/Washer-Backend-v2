package team.washer.server.v2.domain.reservation.enums;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("ReservationStatus 클래스는")
class ReservationStatusTest {

    @Nested
    @DisplayName("RESERVED 상태의")
    class Describe_reserved_status {

        @Test
        @DisplayName("예약 대기 시간을 5분으로 제공해야 한다")
        void it_has_five_minute_timeout() {
            // When
            final int timeoutMinutes = ReservationStatus.RESERVED.getTimeoutMinutes();

            // Then
            assertThat(timeoutMinutes).isEqualTo(5);
        }

        @Test
        @DisplayName("타임아웃 대상이어야 한다")
        void it_has_timeout() {
            // When & Then
            assertThat(ReservationStatus.RESERVED.hasTimeout()).isTrue();
        }
    }

    @Nested
    @DisplayName("종료 상태의")
    class Describe_terminal_status {

        @Test
        @DisplayName("타임아웃 대상이 아니어야 한다")
        void it_has_no_timeout() {
            // When & Then
            assertThat(ReservationStatus.COMPLETED.hasTimeout()).isFalse();
            assertThat(ReservationStatus.CANCELLED.hasTimeout()).isFalse();
        }
    }
}
