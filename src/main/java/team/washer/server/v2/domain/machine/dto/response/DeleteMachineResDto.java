package team.washer.server.v2.domain.machine.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "기기 삭제 응답 DTO")
public record DeleteMachineResDto(@Schema(description = "삭제된 기기 ID", example = "1") Long id,
        @Schema(description = "삭제된 기기명", example = "W-2F-L1") String name) {
}
