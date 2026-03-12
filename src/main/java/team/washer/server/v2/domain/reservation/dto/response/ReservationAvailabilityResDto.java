package team.washer.server.v2.domain.reservation.dto.response;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "예약 가능 상태 DTO")
public record ReservationAvailabilityResDto(@Schema(description = "예약 가능 여부", example = "false") boolean canReserve,
        @Schema(description = "패널티 만료 시간 (패널티 없을 경우 null)", example = "2026-03-12T21:30:00") LocalDateTime penaltyExpiresAt) {
}
