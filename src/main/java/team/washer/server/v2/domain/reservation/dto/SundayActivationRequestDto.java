package team.washer.server.v2.domain.reservation.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "일요일 예약 활성화/비활성화 요청 DTO")
public record SundayActivationRequestDto(
        @Schema(description = "활성화/비활성화 메모", example = "정기 점검으로 인한 활성화") String notes) {
}
