package team.washer.server.v2.domain.malfunction.dto.response;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "고장 신고 목록 응답 DTO")
public record MalfunctionReportListResDto(@Schema(description = "고장 신고 목록") List<MalfunctionReportResDto> reports,
        @Schema(description = "총 신고 수", example = "5") Integer totalCount) {
}
