package team.washer.server.v2.domain.reservation.service;

import team.washer.server.v2.domain.reservation.dto.response.ReservationResDto;

public interface ConfirmReservationService {

    /**
     * 예약을 확인합니다.
     *
     * @param userId
     *            사용자 ID
     * @param reservationId
     *            예약 ID
     * @return 확인된 예약 응답 DTO
     */
    ReservationResDto confirmReservation(Long userId, Long reservationId);
}
