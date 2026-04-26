package team.washer.server.v2.domain.reservation.dto.response;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "기기별 예약 히스토리 응답 DTO")
public record AdminMachineHistoryResDto(
        @Schema(description = "기기별 예약 히스토리 목록") List<AdminMachineHistoryGroupResDto> machines) {
}
