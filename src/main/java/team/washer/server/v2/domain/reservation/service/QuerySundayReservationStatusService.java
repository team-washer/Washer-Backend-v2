package team.washer.server.v2.domain.reservation.service;

import team.washer.server.v2.domain.reservation.dto.response.SundayStatusResDto;

public interface QuerySundayReservationStatusService {
    SundayStatusResDto execute();
}
