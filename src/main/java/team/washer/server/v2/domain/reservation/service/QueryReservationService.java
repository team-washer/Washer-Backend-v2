package team.washer.server.v2.domain.reservation.service;

import team.washer.server.v2.domain.reservation.dto.response.ReservationResDto;

public interface QueryReservationService {

    /**
     * 예약을 조회합니다.
     *
     * @param reservationId
     *            예약 ID
     * @return 예약 응답 DTO
     */
    ReservationResDto queryReservation(Long reservationId);
}
