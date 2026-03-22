package team.washer.server.v2.domain.machine.dto.response;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "기기 목록 응답 DTO")
public record MachineListResDto(@Schema(description = "기기 목록") List<MachineResDto> machines,
        @Schema(description = "총 기기 개수", example = "100") long totalCount,
        @Schema(description = "총 페이지 수", example = "10") int totalPages,
        @Schema(description = "현재 페이지 번호", example = "0") int currentPage) {
}
