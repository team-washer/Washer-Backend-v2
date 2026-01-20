package team.washer.server.v2.domain.reservation.dto.response;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import team.washer.server.v2.domain.reservation.enums.CycleAction;

@Schema(description = "일요일 예약 활성화 히스토리 DTO")
public record SundayActivationResDto(@Schema(description = "로그 ID", example = "1") Long id,
        @Schema(description = "활성화 상태", example = "true") Boolean isActive,
        @Schema(description = "액션", example = "ACTIVATED") CycleAction action,
        @Schema(description = "수행자 이름", example = "김철수") String performedByName,
        @Schema(description = "수행자 학번", example = "20210001") String performedByStudentId,
        @Schema(description = "메모", example = "정기 점검으로 인한 활성화") String notes,
        @Schema(description = "생성 시간", example = "2026-01-27T21:00:00") LocalDateTime createdAt) {
}
