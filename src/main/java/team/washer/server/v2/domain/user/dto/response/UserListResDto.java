package team.washer.server.v2.domain.user.dto.response;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "사용자 목록 응답 DTO")
public record UserListResDto(@Schema(description = "사용자 목록") List<UserResDto> users,
        @Schema(description = "총 사용자 수", example = "5") Integer totalCount) {
}
