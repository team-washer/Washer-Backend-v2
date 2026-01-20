package team.washer.server.v2.domain.reservation.service;

import team.washer.server.v2.domain.reservation.dto.response.CancellationResDto;

public interface CancelReservationService {

    /**
     * 예약을 취소합니다.
     *
     * @param userId
     *            사용자 ID
     * @param reservationId
     *            예약 ID
     * @return 취소 응답 DTO
     */
    CancellationResDto cancelReservation(Long userId, Long reservationId);
}
