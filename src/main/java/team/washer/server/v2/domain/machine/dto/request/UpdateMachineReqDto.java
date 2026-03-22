package team.washer.server.v2.domain.machine.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import team.washer.server.v2.domain.machine.enums.MachineType;
import team.washer.server.v2.domain.machine.enums.Position;

@Schema(description = "기기 수정 요청 DTO")
public record UpdateMachineReqDto(@Schema(description = "기기 유형 (null이면 유지)", example = "WASHER") MachineType type,
        @Schema(description = "층 (null이면 유지)", example = "2") @Min(value = 1, message = "층은 1 이상이어야 합니다") Integer floor,
        @Schema(description = "위치 (null이면 유지)", example = "LEFT") Position position,
        @Schema(description = "번호 (null이면 유지)", example = "1") @Min(value = 1, message = "번호는 1 이상이어야 합니다") Integer number,
        @Schema(description = "SmartThings Device ID (null이면 유지)", example = "abc-123-def-456") @Size(max = 100, message = "Device ID는 100자를 초과할 수 없습니다") String deviceId) {
}
