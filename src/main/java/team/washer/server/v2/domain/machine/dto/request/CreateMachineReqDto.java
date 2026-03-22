package team.washer.server.v2.domain.machine.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import team.washer.server.v2.domain.machine.enums.MachineType;
import team.washer.server.v2.domain.machine.enums.Position;

@Schema(description = "기기 등록 요청 DTO")
public record CreateMachineReqDto(
        @Schema(description = "기기 유형", example = "WASHER", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull(message = "기기 유형은 필수입니다") MachineType type,
        @Schema(description = "층", example = "2", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull(message = "층은 필수입니다") @Min(value = 1, message = "층은 1 이상이어야 합니다") Integer floor,
        @Schema(description = "위치", example = "LEFT", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull(message = "위치는 필수입니다") Position position,
        @Schema(description = "번호", example = "1", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull(message = "번호는 필수입니다") @Min(value = 1, message = "번호는 1 이상이어야 합니다") Integer number,
        @Schema(description = "SmartThings Device ID", example = "abc-123-def-456", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank(message = "SmartThings Device ID는 필수입니다") @Size(max = 100, message = "Device ID는 100자를 초과할 수 없습니다") String deviceId) {
}
