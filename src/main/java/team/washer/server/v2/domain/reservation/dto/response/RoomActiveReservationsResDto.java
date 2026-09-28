package team.washer.server.v2.domain.reservation.dto.response;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "호실 활성 예약 목록 응답 DTO")
public record RoomActiveReservationsResDto(
        @Schema(description = "호실의 활성 예약 목록. 활성 예약이 없으면 빈 배열([])입니다.") List<ReservationResDto> reservations) {
}
