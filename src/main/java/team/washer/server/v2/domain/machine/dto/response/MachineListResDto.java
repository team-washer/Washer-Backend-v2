package team.washer.server.v2.domain.machine.dto.response;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "기기 목록 응답 DTO")
public record MachineListResDto(@Schema(description = "기기 목록") List<MachineResDto> machines,
        @Schema(description = "총 개수", example = "10") int totalCount) {
}
