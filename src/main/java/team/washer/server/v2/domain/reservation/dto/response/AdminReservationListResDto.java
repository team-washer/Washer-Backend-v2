package team.washer.server.v2.domain.reservation.dto.response;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "관리자용 예약 목록 응답 DTO")
public record AdminReservationListResDto(@Schema(description = "예약 목록") List<AdminReservationResDto> reservations,
        @Schema(description = "총 예약 개수", example = "100") long totalCount,
        @Schema(description = "총 페이지 수", example = "10") int totalPages,
        @Schema(description = "현재 페이지 번호", example = "0") int currentPage) {
}
