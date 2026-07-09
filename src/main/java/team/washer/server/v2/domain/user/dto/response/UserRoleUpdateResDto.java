package team.washer.server.v2.domain.user.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import team.washer.server.v2.domain.user.enums.UserRole;

@Schema(description = "사용자 권한 변경 응답 DTO")
public record UserRoleUpdateResDto(@Schema(description = "사용자 ID", example = "1") Long id,
        @Schema(description = "이름", example = "김철수") String name,
        @Schema(description = "학번", example = "20241234") String studentId,
        @Schema(description = "변경된 권한", example = "DORMITORY_COUNCIL") UserRole role) {
}
