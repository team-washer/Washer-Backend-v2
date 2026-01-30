package team.washer.server.v2.domain.reservation.dto.response;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "관리자 예약 강제 취소 응답 DTO")
public record AdminCancellationResDto(@Schema(description = "취소된 예약 ID", example = "1") Long reservationId,
        @Schema(description = "사용자 이름", example = "김철수") String userName,
        @Schema(description = "기기명", example = "W-2F-L1") String machineName,
        @Schema(description = "취소 시간", example = "2026-01-29T12:30:00") LocalDateTime cancelledAt,
        @Schema(description = "패널티 부과 여부", example = "false") boolean penaltyApplied) {
}
