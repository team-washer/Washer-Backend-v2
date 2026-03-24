package team.washer.server.v2.domain.reservation.service;

import java.util.List;

import team.washer.server.v2.domain.reservation.dto.response.ReservationResDto;

public interface QueryRoomActiveReservationsService {

    List<ReservationResDto> execute();
}
