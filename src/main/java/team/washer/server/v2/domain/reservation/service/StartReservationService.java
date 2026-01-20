package team.washer.server.v2.domain.reservation.service;

import team.washer.server.v2.domain.reservation.dto.request.StartReservationReqDto;
import team.washer.server.v2.domain.reservation.dto.response.ReservationResDto;

public interface StartReservationService {

    /**
     * 예약을 시작합니다.
     *
     * @param userId
     *            사용자 ID
     * @param reservationId
     *            예약 ID
     * @param reqDto
     *            시작 요청 DTO
     * @return 시작된 예약 응답 DTO
     */
    ReservationResDto execute(Long userId, Long reservationId, StartReservationReqDto reqDto);
}
