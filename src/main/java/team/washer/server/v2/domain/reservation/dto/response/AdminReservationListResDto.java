package team.washer.server.v2.domain.reservation.dto.response;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "관리자용 예약 목록 응답 DTO")
public record AdminReservationListResDto(@Schema(description = "예약 목록") List<AdminReservationResDto> reservations,
        @Schema(description = "총 개수", example = "10") int totalCount) {
}
