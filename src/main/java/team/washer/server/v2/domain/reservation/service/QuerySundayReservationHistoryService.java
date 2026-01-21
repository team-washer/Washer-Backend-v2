package team.washer.server.v2.domain.reservation.service;

import java.util.List;

import team.washer.server.v2.domain.reservation.entity.ReservationCycleLog;

/**
 * 일요일 예약 활성화 히스토리 조회 서비스
 */
public interface QuerySundayReservationHistoryService {

    /**
     * 일요일 예약 활성화 히스토리 조회
     *
     * @return 활성화 히스토리 목록
     */
    List<ReservationCycleLog> execute();
}
