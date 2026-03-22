package team.washer.server.v2.domain.machine.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import team.washer.server.v2.domain.machine.enums.MachineAvailability;
import team.washer.server.v2.domain.machine.enums.MachineStatus;
import team.washer.server.v2.domain.machine.enums.MachineType;
import team.washer.server.v2.domain.machine.enums.Position;

@Schema(description = "기기 응답 DTO")
public record MachineResDto(@Schema(description = "기기 ID", example = "1") Long id,
        @Schema(description = "기기명", example = "W-2F-L1") String name,
        @Schema(description = "기기 유형", example = "WASHER") MachineType type,
        @Schema(description = "층", example = "2") Integer floor,
        @Schema(description = "위치", example = "LEFT") Position position,
        @Schema(description = "번호", example = "1") Integer number,
        @Schema(description = "기기 상태", example = "NORMAL") MachineStatus status,
        @Schema(description = "사용 가능 여부", example = "AVAILABLE") MachineAvailability availability,
        @Schema(description = "SmartThings Device ID", example = "abc-123") String deviceId) {
}
