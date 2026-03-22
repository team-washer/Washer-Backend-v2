package team.washer.server.v2.domain.admin.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "관리자 대시보드 통계 응답")
public record AdminDashboardResDto(@Schema(description = "활성 예약 수", example = "5") Long activeReservations,

        @Schema(description = "대기 중인 고장 신고 수", example = "3") Long pendingMalfunctionReports,

        @Schema(description = "처리 중인 고장 신고 수", example = "2") Long processingMalfunctionReports,

        @Schema(description = "처리 완료된 고장 신고 수", example = "10") Long completedMalfunctionReports) {
}
