package team.washer.server.v2.domain.machine.dto.response;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "기기 상태 목록 응답 DTO")
public record MachineStatusListResDto(@Schema(description = "기기 상태 목록") List<MachineStatusResDto> machines,
        @Schema(description = "총 기기 수", example = "10") Integer totalCount) {
}
