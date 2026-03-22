package team.washer.server.v2.domain.reservation.dto.response;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "패널티 상태 DTO")
public record PenaltyStatusResDto(@Schema(description = "사용자 ID", example = "1") Long userId,
        @Schema(description = "패널티 적용 여부", example = "true") boolean isPenalized,
        @Schema(description = "패널티 만료 시간", example = "2026-01-27T21:30:00") LocalDateTime penaltyExpiresAt,
        @Schema(description = "남은 시간 (분)", example = "5") Long remainingMinutes) {
}
