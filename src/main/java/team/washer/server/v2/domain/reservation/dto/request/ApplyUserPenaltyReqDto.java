package team.washer.server.v2.domain.reservation.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "세탁 패널티 부과 요청 DTO")
public record ApplyUserPenaltyReqDto(
        @NotBlank(message = "부과 사유는 필수입니다") @Size(max = 200, message = "부과 사유는 200자를 초과할 수 없습니다") @Schema(description = "패널티 부과 사유", example = "세탁물 장기 방치로 기기 점유") String reason) {
}
