package team.washer.server.v2.domain.malfunction.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import team.washer.server.v2.domain.malfunction.enums.MalfunctionReportStatus;

@Schema(description = "고장 신고 상태 변경 요청 DTO")
public record UpdateMalfunctionReportStatusReqDto(
        @NotNull(message = "상태는 필수입니다") @Schema(description = "변경할 상태", example = "IN_PROGRESS") MalfunctionReportStatus status) {
}
