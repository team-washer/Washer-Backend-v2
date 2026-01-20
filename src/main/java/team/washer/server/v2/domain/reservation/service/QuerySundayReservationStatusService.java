package team.washer.server.v2.domain.reservation.service;

import team.washer.server.v2.domain.reservation.dto.response.SundayStatusResDto;

public interface GetSundayReservationStatusService {

    /**
     * 일요일 예약 상태를 조회합니다.
     *
     * @return 일요일 예약 상태 및 히스토리
     */
    SundayStatusResDto getSundayReservationStatus();
}
