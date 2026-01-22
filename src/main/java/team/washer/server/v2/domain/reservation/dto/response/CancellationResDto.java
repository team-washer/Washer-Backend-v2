package team.washer.server.v2.domain.reservation.dto.response;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "예약 취소 응답 DTO")
public record CancellationResDto(@Schema(description = "취소 성공 여부", example = "true") boolean success,
        @Schema(description = "메시지", example = "예약이 취소되었습니다") String message,
        @Schema(description = "패널티 적용 여부", example = "true") boolean penaltyApplied,
        @Schema(description = "패널티 만료 시간", example = "2026-01-27T21:30:00") LocalDateTime penaltyExpiresAt) {
}
