package team.washer.server.v2.domain.user.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import team.washer.server.v2.domain.user.enums.UserRole;

@Schema(description = "사용자 권한 변경 요청 DTO")
public record UpdateUserRoleReqDto(
        @Schema(description = "변경할 권한", example = "DORMITORY_COUNCIL") @NotNull(message = "권한은 필수입니다") UserRole role) {
}
