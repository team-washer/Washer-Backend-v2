package team.washer.server.v2.domain.machine.dto.response;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import team.washer.server.v2.domain.smartthings.enums.MachineOperatingState;

@Schema(description = "기기 실시간 SmartThings 상태 응답")
public record MachineDeviceStatusResDto(@Schema(description = "기기 ID", example = "1") Long machineId,

        @Schema(description = "작동 상태", example = "run") MachineOperatingState operatingState,

        @Schema(description = "작업 상태", example = "wash") String jobState,

        @Schema(description = "전원 상태", example = "on") String switchStatus,

        @Schema(description = "완료 예정 시간", example = "2026-01-26T15:30:00") LocalDateTime expectedCompletionTime,

        @Schema(description = "남은 시간(분)", example = "30") Long remainingMinutes) {
}
