package team.washer.server.v2.domain.reservation.dto.response;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "호실 활성 예약 목록 응답 DTO")
public record RoomActiveReservationsResDto(@Schema(description = "호실 활성 예약 목록") List<ReservationResDto> reservations) {
}
