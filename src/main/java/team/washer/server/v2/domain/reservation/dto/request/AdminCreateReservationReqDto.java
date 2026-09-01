package team.washer.server.v2.domain.reservation.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "관리자 대리 예약 생성 요청 DTO")
public record AdminCreateReservationReqDto(
        @NotNull(message = "사용자 ID는 필수입니다") @Schema(description = "예약 주체가 될 사용자 ID", example = "12") Long userId,
        @NotNull(message = "기기 ID는 필수입니다") @Schema(description = "기기 ID", example = "1") Long machineId) {
}
