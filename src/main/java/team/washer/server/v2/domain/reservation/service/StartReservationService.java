package team.washer.server.v2.domain.reservation.service;

import team.washer.server.v2.domain.reservation.dto.request.StartReservationReqDto;
import team.washer.server.v2.domain.reservation.dto.response.ReservationResDto;

public interface StartReservationService {
    ReservationResDto execute(Long userId, Long reservationId, StartReservationReqDto reqDto);
}
