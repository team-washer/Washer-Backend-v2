package team.washer.server.v2.domain.reservation.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "일요일 예약 상태 DTO")
public record SundayStatusDto(@Schema(description = "활성화 상태", example = "true") boolean isActive,
        @Schema(description = "최근 히스토리 (최대 10개)") List<SundayActivationDto> history) {

    public static SundayStatusDto of(boolean isActive, List<SundayActivationDto> history) {
        return new SundayStatusDto(isActive, history);
    }
}
