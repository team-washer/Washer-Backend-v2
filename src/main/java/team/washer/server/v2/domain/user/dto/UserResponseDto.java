package team.washer.server.v2.domain.user.dto;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import team.washer.server.v2.domain.user.entity.User;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "사용자 응답 DTO")
public class UserResponseDto {

    @Schema(description = "사용자 ID", example = "1")
    private Long id;

    @Schema(description = "이름", example = "김철수")
    private String name;

    @Schema(description = "학번", example = "20210001")
    private String studentId;

    @Schema(description = "호실", example = "301")
    private String roomNumber;

    @Schema(description = "학년", example = "3")
    private Integer grade;

    @Schema(description = "층", example = "3")
    private Integer floor;

    @Schema(description = "패널티 횟수", example = "0")
    private Integer penaltyCount;

    @Schema(description = "생성 시간", example = "2025-01-15T14:30:00")
    private LocalDateTime createdAt;

    @Schema(description = "수정 시간", example = "2025-01-15T14:30:00")
    private LocalDateTime updatedAt;

    public static UserResponseDto from(User user) {
        return UserResponseDto.builder().id(user.getId()).name(user.getName()).studentId(user.getStudentId())
                .roomNumber(user.getRoomNumber()).grade(user.getGrade()).floor(user.getFloor())
                .penaltyCount(user.getPenaltyCount()).createdAt(user.getCreatedAt()).updatedAt(user.getUpdatedAt())
                .build();
    }
}
