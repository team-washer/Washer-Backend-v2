package team.washer.server.v2.domain.reservation.service;

import team.washer.server.v2.domain.reservation.dto.response.ReservationResDto;

public interface QueryActiveReservationService {
    ReservationResDto execute(Long userId);
}
