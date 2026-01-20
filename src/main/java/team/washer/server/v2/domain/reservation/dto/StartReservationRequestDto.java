package team.washer.server.v2.domain.reservation.dto;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "예약 시작 요청 DTO")
public record StartReservationRequestDto(
        @NotNull(message = "예상 완료 시간은 필수입니다") @Schema(description = "예상 완료 시간", example = "2026-01-27T23:00:00") LocalDateTime expectedCompletionTime) {
}
