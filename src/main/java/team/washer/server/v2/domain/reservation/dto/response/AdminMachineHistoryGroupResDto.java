package team.washer.server.v2.domain.reservation.dto.response;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "기기별 예약 히스토리 그룹 DTO")
public record AdminMachineHistoryGroupResDto(@Schema(description = "기기명", example = "W-2F-L1") String machineName,
        @Schema(description = "예약 히스토리 목록") List<AdminMachineReservationItemResDto> reservations) {
}
