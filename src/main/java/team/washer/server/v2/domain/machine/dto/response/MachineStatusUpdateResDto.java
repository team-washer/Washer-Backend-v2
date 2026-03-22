package team.washer.server.v2.domain.machine.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import team.washer.server.v2.domain.machine.enums.MachineAvailability;
import team.washer.server.v2.domain.machine.enums.MachineStatus;

@Schema(description = "기기 상태 변경 응답 DTO")
public record MachineStatusUpdateResDto(@Schema(description = "기기 ID", example = "1") Long id,
        @Schema(description = "기기명", example = "W-2F-L1") String name,
        @Schema(description = "변경된 상태", example = "NORMAL") MachineStatus status,
        @Schema(description = "변경된 사용 가능 여부", example = "AVAILABLE") MachineAvailability availability) {
}
