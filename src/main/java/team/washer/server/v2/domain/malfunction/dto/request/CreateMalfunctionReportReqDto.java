package team.washer.server.v2.domain.malfunction.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "고장 신고 생성 요청 DTO")
public record CreateMalfunctionReportReqDto(
        @NotNull(message = "기기 ID는 필수입니다") @Schema(description = "기기 ID", example = "1") Long machineId,
        @NotBlank(message = "신고 내용은 필수입니다") @Size(max = 200, message = "신고 내용은 200자를 초과할 수 없습니다") @Schema(description = "신고 내용", example = "세탁기 작동이 되지 않습니다") String description) {
}
