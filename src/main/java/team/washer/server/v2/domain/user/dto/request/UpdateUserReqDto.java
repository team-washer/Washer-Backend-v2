package team.washer.server.v2.domain.user.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

@Schema(description = "사용자 정보 수정 요청 DTO")
public record UpdateUserReqDto(
        @Schema(description = "호실", example = "301") @Pattern(regexp = "^\\d{3,4}$", message = "호실은 3-4자리 숫자여야 합니다") String roomNumber,
        @Schema(description = "학년", example = "2") @Min(value = 1, message = "학년은 1 이상이어야 합니다") @Max(value = 4, message = "학년은 4 이하여야 합니다") Integer grade,
        @Schema(description = "층", example = "3") @Min(value = 1, message = "층은 1 이상이어야 합니다") Integer floor) {
}
