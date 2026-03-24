package team.washer.server.v2.domain.reservation.service;

import team.washer.server.v2.domain.reservation.dto.response.RoomActiveReservationsResDto;

public interface QueryRoomActiveReservationsService {

    RoomActiveReservationsResDto execute();
}
