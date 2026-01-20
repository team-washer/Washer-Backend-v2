package team.washer.server.v2.domain.reservation.dto;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "예약 생성 요청 DTO")
public record CreateReservationRequestDto(
        @NotNull(message = "기기 ID는 필수입니다") @Schema(description = "기기 ID", example = "1") Long machineId,
        @NotNull(message = "시작 시간은 필수입니다") @Schema(description = "예약 시작 시간", example = "2026-01-27T21:30:00") LocalDateTime startTime) {
}
