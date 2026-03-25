package team.washer.server.v2.domain.malfunction.dto.response;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "고장 신고 목록 응답 DTO")
public record MalfunctionReportListResDto(@Schema(description = "고장 신고 목록") List<MalfunctionReportResDto> reports,
        @Schema(description = "총 신고 수", example = "5") long totalCount,
        @Schema(description = "총 페이지 수", example = "3") int totalPages,
        @Schema(description = "현재 페이지 번호 (0부터 시작)", example = "0") int currentPage) {
}
