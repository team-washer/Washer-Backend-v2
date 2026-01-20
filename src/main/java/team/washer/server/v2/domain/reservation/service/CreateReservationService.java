package team.washer.server.v2.domain.reservation.service;

import team.washer.server.v2.domain.reservation.dto.request.CreateReservationReqDto;
import team.washer.server.v2.domain.reservation.dto.response.ReservationResDto;

public interface CreateReservationService {

    /**
     * 예약을 생성합니다.
     *
     * @param userId
     *            사용자 ID
     * @param reqDto
     *            예약 생성 요청 DTO
     * @return 생성된 예약 응답 DTO
     */
    ReservationResDto createReservation(Long userId, CreateReservationReqDto reqDto);
}
