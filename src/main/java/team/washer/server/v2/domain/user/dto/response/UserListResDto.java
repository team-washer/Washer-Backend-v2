package team.washer.server.v2.domain.user.dto.response;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "사용자 목록 응답 DTO")
public record UserListResDto(@Schema(description = "사용자 목록") List<UserResDto> users,
        @Schema(description = "총 사용자 수", example = "5") long totalCount,
        @Schema(description = "총 페이지 수", example = "3") int totalPages,
        @Schema(description = "현재 페이지 번호 (0부터 시작)", example = "0") int currentPage) {
}
