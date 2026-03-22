package team.washer.server.v2.domain.user.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "사용자 수정 응답 DTO")
public record UserUpdateResDto(@Schema(description = "사용자 ID", example = "1") Long id,
        @Schema(description = "이름", example = "김철수") String name,
        @Schema(description = "학번", example = "20241234") String studentId,
        @Schema(description = "호실", example = "301") String roomNumber,
        @Schema(description = "학년", example = "2") Integer grade,
        @Schema(description = "층", example = "3") Integer floor) {
}
