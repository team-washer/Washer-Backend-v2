package team.washer.server.v2.domain.reservation.service;

import team.washer.server.v2.domain.reservation.dto.response.CancellationResDto;

public interface CancelReservationService {
    CancellationResDto execute(Long userId, Long reservationId);
}
