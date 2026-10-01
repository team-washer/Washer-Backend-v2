package team.washer.server.v2.domain.admin.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "관리자 대시보드 통계 응답")
public record AdminDashboardResDto(@Schema(description = "활성 예약 수", example = "5") Long activeReservations,

        @Schema(description = "대기 중인 고장 신고 수", example = "3") Long pendingMalfunctionReports,

        @Schema(description = "처리 중인 고장 신고 수", example = "2") Long processingMalfunctionReports,

        @Schema(description = "처리 완료된 고장 신고 수", example = "10") Long completedMalfunctionReports,

        @Schema(description = "전체 기기 수", example = "8") Long totalMachines,

        @Schema(description = "고장 상태 기기 수", example = "2") Long malfunctionMachines,

        @Schema(description = "현재 예약 생성이 제한된 학생 수 (쿨다운·호실 차단·세탁 금지 대상, 중복 제외)", example = "1") Long suspendedStudents) {
}
