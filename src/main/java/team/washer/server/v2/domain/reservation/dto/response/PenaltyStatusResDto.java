package team.washer.server.v2.domain.reservation.dto.response;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "패널티 상태 DTO")
public record PenaltyStatusResDto(@Schema(description = "사용자 ID", example = "1") Long userId,
        @Schema(description = "패널티 적용 여부 (쿨다운 또는 호실 차단 중 하나라도 활성이면 true)", example = "true") boolean isPenalized,
        @Schema(description = "패널티 만료 시간 (쿨다운·차단 중 가장 늦은 시각)", example = "2026-01-27T21:30:00") LocalDateTime penaltyExpiresAt,
        @Schema(description = "남은 시간 (분)", example = "5") Long remainingMinutes,
        @Schema(description = "48시간 호실 차단 여부 (연장 가능 여부 판단용)", example = "false") boolean isRoomBlocked,
        @Schema(description = "호실 차단 만료 시간", example = "2026-01-29T12:00:00") LocalDateTime blockExpiresAt) {
}
