package team.washer.server.v2.domain.malfunction.dto.response;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import team.washer.server.v2.domain.malfunction.enums.MalfunctionReportStatus;

@Schema(description = "고장 신고 응답 DTO")
public record MalfunctionReportResDto(@Schema(description = "신고 ID", example = "1") Long id,
        @Schema(description = "기기 ID", example = "1") Long machineId,
        @Schema(description = "기기명", example = "WASHER-3F-L1") String machineName,
        @Schema(description = "신고자 ID", example = "1") Long reporterId,
        @Schema(description = "신고자 이름", example = "김철수") String reporterName,
        @Schema(description = "신고 내용", example = "세탁기 작동이 되지 않습니다") String description,
        @Schema(description = "신고 상태", example = "PENDING") MalfunctionReportStatus status,
        @Schema(description = "신고 시간", example = "2026-01-23T14:30:00") LocalDateTime reportedAt,
        @Schema(description = "처리 시작 시간", example = "2026-01-23T15:00:00") LocalDateTime processingStartedAt,
        @Schema(description = "처리 완료 시간", example = "2026-01-23T16:00:00") LocalDateTime resolvedAt,
        @Schema(description = "생성 시간", example = "2026-01-23T14:30:00") LocalDateTime createdAt,
        @Schema(description = "수정 시간", example = "2026-01-23T14:30:00") LocalDateTime updatedAt) {
}
