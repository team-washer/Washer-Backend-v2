package team.washer.server.v2.domain.machine.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import team.washer.server.v2.domain.machine.enums.MachineStatus;

@Schema(description = "기기 상태 변경 요청 DTO")
public record UpdateMachineStatusReqDto(
        @Schema(description = "기기 상태", example = "NORMAL", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull(message = "기기 상태는 필수입니다") MachineStatus status) {
}
