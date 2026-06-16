package team.washer.server.v2.domain.admin.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

@Schema(description = "호실 세탁 금지 등록 요청 DTO")
public record CreateWashingBanReqDto(
        @NotBlank(message = "호실은 필수입니다") @Pattern(regexp = "^\\d{3,4}$", message = "호실은 3-4자리 숫자여야 합니다") @Schema(description = "금지할 호실 번호", example = "301") String roomNumber) {
}
