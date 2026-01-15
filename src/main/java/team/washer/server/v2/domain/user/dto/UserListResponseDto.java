package team.washer.server.v2.domain.user.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "사용자 목록 응답 DTO")
public record UserListResponseDto(@Schema(description = "사용자 목록") List<UserResponseDto> users,
        @Schema(description = "총 사용자 수", example = "5") Integer totalCount) {
}
