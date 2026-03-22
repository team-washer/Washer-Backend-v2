package team.washer.server.v2.domain.reservation.dto.response;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import team.washer.server.v2.domain.reservation.enums.ReservationStatus;

@Schema(description = "기기별 예약 히스토리 DTO")
public record MachineReservationHistoryResDto(@Schema(description = "예약 ID", example = "1") Long id,
        @Schema(description = "사용자 호실", example = "301") String userRoomNumber,
        @Schema(description = "시작 시간", example = "2026-01-27T21:30:00") LocalDateTime startTime,
        @Schema(description = "완료 시간", example = "2026-01-27T23:00:00") LocalDateTime completionTime,
        @Schema(description = "예약 상태", example = "COMPLETED") ReservationStatus status,
        @Schema(description = "생성 시간", example = "2026-01-27T21:00:00") LocalDateTime createdAt) {
}
