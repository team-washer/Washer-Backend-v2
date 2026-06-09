package team.washer.server.v2.domain.reservation.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@Schema(description = "예약 차단 기간 연장 요청 DTO")
public record ExtendBlockReqDto(
        @Min(value = 1, message = "연장 일수는 1일 이상이어야 합니다") @Max(value = 30, message = "연장 일수는 최대 30일까지 가능합니다") @Schema(description = "연장 일수", example = "2") int days) {
}
