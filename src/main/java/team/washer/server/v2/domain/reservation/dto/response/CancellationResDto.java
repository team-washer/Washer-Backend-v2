package team.washer.server.v2.domain.reservation.dto.response;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "예약 취소 응답 DTO")
public record CancellationResDto(@Schema(description = "취소 성공 여부", example = "true") boolean success,
        @Schema(description = "메시지", example = "예약이 취소되었습니다") String message,
        @Schema(description = "패널티 적용 여부", example = "true") boolean penaltyApplied,
        @Schema(description = "이번 취소로 적용된 동일 종류 기기 재예약 제한의 만료 시간 (패널티 없을 경우 null, 호실 차단 만료 시간은 포함하지 않음)", example = "2026-01-27T21:30:00") LocalDateTime penaltyExpiresAt) {
}
