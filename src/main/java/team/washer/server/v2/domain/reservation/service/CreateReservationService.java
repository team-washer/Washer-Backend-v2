package team.washer.server.v2.domain.reservation.service;

import team.washer.server.v2.domain.reservation.dto.request.CreateReservationReqDto;
import team.washer.server.v2.domain.reservation.dto.response.ReservationResDto;

public interface CreateReservationService {
    ReservationResDto execute(Long userId, CreateReservationReqDto reqDto);
}
