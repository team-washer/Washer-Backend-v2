package team.washer.server.v2.domain.reservation.service;

import team.washer.server.v2.domain.reservation.dto.response.ReservationResDto;

public interface ConfirmReservationService {
    ReservationResDto execute(Long userId, Long reservationId);
}
