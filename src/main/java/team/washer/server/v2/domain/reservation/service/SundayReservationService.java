package team.washer.server.v2.domain.reservation.service;

import java.util.List;

import team.washer.server.v2.domain.reservation.entity.ReservationCycleLog;
import team.washer.server.v2.domain.user.entity.User;

public interface SundayReservationService {

    /**
     * 일요일 예약 활성화
     *
     * @param performedBy 활성화를 수행한 사용자
     * @param notes 활성화 메모
     */
    void activateSundayReservation(User performedBy, String notes);

    /**
     * 일요일 예약 비활성화
     *
     * @param performedBy 비활성화를 수행한 사용자
     * @param notes 비활성화 메모
     */
    void deactivateSundayReservation(User performedBy, String notes);

    /**
     * 일요일 예약 활성화 상태 확인
     *
     * @return 활성화되어 있으면 true
     */
    boolean isSundayReservationActive();

    /**
     * 일요일 예약 활성화 히스토리 조회
     *
     * @return 활성화 히스토리 목록
     */
    List<ReservationCycleLog> getSundayReservationHistory();
}
