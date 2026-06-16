package team.washer.server.v2.domain.machine.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import team.washer.server.v2.domain.machine.enums.ForceStopResult;
import team.washer.server.v2.domain.machine.enums.MachineAvailability;
import team.washer.server.v2.domain.machine.enums.MachineType;

@Schema(description = "기기 강제 정지 응답 DTO")
public record ForceStopMachineResDto(@Schema(description = "기기 ID", example = "1") Long machineId,
        @Schema(description = "기기명", example = "W-2F-L1") String machineName,
        @Schema(description = "기기 타입", example = "WASHER") MachineType machineType,
        @Schema(description = "SmartThings Device ID", example = "device-abc") String deviceId,
        @Schema(description = "SmartThings 강제 정지 처리 결과", example = "STOPPED") ForceStopResult forceStopResult,
        @Schema(description = "처리 전 기기 동작 상태", example = "run") String previousMachineState,
        @Schema(description = "취소된 활성 예약 ID", example = "1") Long cancelledReservationId,
        @Schema(description = "활성 예약 취소 여부", example = "true") boolean reservationCancelled,
        @Schema(description = "변경된 기기 사용 가능 상태", example = "AVAILABLE") MachineAvailability availability) {
}
