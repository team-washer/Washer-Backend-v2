package team.washer.server.v2.domain.reservation.dto.response;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import team.washer.server.v2.domain.reservation.enums.ReservationStatus;

@Schema(description = "기기별 예약 히스토리 항목 DTO")
public record AdminMachineReservationItemResDto(
        @Schema(description = "예약 호실", example = "201") String roomNumber,
        @Schema(description = "예약 시간", example = "2026-04-08T10:00:00") LocalDateTime reservedAt,
        @Schema(description = "실제 완료 시간 (완료되지 않은 경우 null)", example = "2026-04-08T11:30:00") LocalDateTime actualCompletionTime,
        @Schema(description = "취소 시간 (취소되지 않은 경우 null)", example = "null") LocalDateTime cancelledAt,
        @Schema(description = "예약 상태", example = "COMPLETED") ReservationStatus status) {
}