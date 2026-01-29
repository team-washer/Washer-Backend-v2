package team.washer.server.v2.domain.machine.dto.response;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import team.washer.server.v2.domain.machine.enums.MachineAvailability;
import team.washer.server.v2.domain.machine.enums.MachineStatus;
import team.washer.server.v2.domain.machine.enums.MachineType;

@Schema(description = "기기 상태 응답")
public record MachineStatusResDto(@Schema(description = "기기 ID", example = "1") Long machineId,

        @Schema(description = "기기명", example = "Washer-3F-L1") String name,

        @Schema(description = "기기 타입", example = "WASHER") MachineType type,

        @Schema(description = "기기 상태", example = "NORMAL") MachineStatus status,

        @Schema(description = "사용 가능 여부", example = "AVAILABLE") MachineAvailability availability,

        @Schema(description = "작동 상태", example = "running") String operatingState,

        @Schema(description = "작업 상태", example = "washing") String jobState,

        @Schema(description = "전원 상태", example = "on") String switchStatus,

        @Schema(description = "완료 예정 시간", example = "2026-01-26T15:30:00") LocalDateTime expectedCompletionTime,

        @Schema(description = "남은 시간(분)", example = "30") Long remainingMinutes,

        @Schema(description = "예약 ID (예약이 있는 경우)", example = "1") Long reservationId,

        @Schema(description = "예약 사용자 ID (예약이 있는 경우)", example = "1") Long userId) {
}
