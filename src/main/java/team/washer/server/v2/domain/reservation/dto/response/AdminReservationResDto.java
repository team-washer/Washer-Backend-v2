package team.washer.server.v2.domain.reservation.dto.response;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import team.washer.server.v2.domain.reservation.enums.ReservationStatus;

@Schema(description = "관리자용 예약 응답 DTO")
public record AdminReservationResDto(@Schema(description = "예약 ID", example = "1") Long id,
        @Schema(description = "사용자 ID", example = "1") Long userId,
        @Schema(description = "사용자 이름", example = "김철수") String userName,
        @Schema(description = "사용자 호실", example = "301") String userRoomNumber,
        @Schema(description = "기기 ID", example = "1") Long machineId,
        @Schema(description = "기기명", example = "W-2F-L1") String machineName,
        @Schema(description = "예약 시간", example = "2026-01-27T21:30:00") LocalDateTime reservedAt,
        @Schema(description = "시작 시간", example = "2026-01-27T21:30:00") LocalDateTime startTime,
        @Schema(description = "예상 완료 시간", example = "2026-01-27T23:00:00") LocalDateTime expectedCompletionTime,
        @Schema(description = "실제 완료 시간", example = "2026-01-27T23:00:00") LocalDateTime actualCompletionTime,
        @Schema(description = "예약 상태", example = "RESERVED") ReservationStatus status,
        @Schema(description = "확인 시간", example = "2026-01-27T21:25:00") LocalDateTime confirmedAt,
        @Schema(description = "취소 시간", example = "2026-01-27T21:20:00") LocalDateTime cancelledAt) {
}
