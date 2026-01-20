package team.washer.server.v2.domain.reservation.service;

import team.washer.server.v2.domain.reservation.dto.response.ReservationResDto;

public interface QueryActiveReservationService {

    /**
     * 활성 예약을 조회합니다.
     *
     * @param userId
     *            사용자 ID
     * @return 활성 예약 응답 DTO (없으면 null)
     */
    ReservationResDto queryActiveReservation(Long userId);
}
