package team.washer.server.v2.domain.user.dto;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "사용자 응답 DTO")
public record UserResponseDto(@Schema(description = "사용자 ID", example = "1") Long id,
        @Schema(description = "이름", example = "김철수") String name,
        @Schema(description = "학번", example = "20210001") String studentId,
        @Schema(description = "호실", example = "301") String roomNumber,
        @Schema(description = "학년", example = "3") Integer grade,
        @Schema(description = "층", example = "3") Integer floor,
        @Schema(description = "패널티 횟수", example = "0") Integer penaltyCount,
        @Schema(description = "생성 시간", example = "2025-01-15T14:30:00") LocalDateTime createdAt,
        @Schema(description = "수정 시간", example = "2025-01-15T14:30:00") LocalDateTime updatedAt) {
}
