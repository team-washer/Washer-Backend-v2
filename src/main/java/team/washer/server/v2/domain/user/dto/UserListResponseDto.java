package team.washer.server.v2.domain.user.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "사용자 목록 응답 DTO")
public class UserListResponseDto {

    @Schema(description = "사용자 목록")
    private List<UserResponseDto> users;

    @Schema(description = "총 사용자 수", example = "5")
    private Integer totalCount;
}
