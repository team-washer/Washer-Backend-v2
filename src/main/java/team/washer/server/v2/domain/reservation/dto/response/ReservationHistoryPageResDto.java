package team.washer.server.v2.domain.reservation.dto.response;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "예약 히스토리 페이지 응답 DTO")
public record ReservationHistoryPageResDto(@Schema(description = "예약 히스토리 목록") List<ReservationHistoryResDto> content,
        @Schema(description = "현재 페이지 번호", example = "0") int pageNumber,
        @Schema(description = "페이지 크기", example = "20") int pageSize,
        @Schema(description = "총 요소 개수", example = "100") long totalElements,
        @Schema(description = "총 페이지 개수", example = "5") int totalPages,
        @Schema(description = "마지막 페이지 여부", example = "false") boolean last) {
}
