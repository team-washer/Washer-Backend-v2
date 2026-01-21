package team.washer.server.v2.domain.reservation.service;

/**
 * 일요일 예약 활성화 상태 조회 서비스
 */
public interface QuerySundayReservationActiveService {

    /**
     * 일요일 예약 활성화 상태 확인
     *
     * @return 활성화되어 있으면 true
     */
    boolean execute();
}
