package team.washer.server.v2.domain.reservation.service;

public interface ActivateSundayReservationService {

    /**
     * 일요일 예약을 활성화합니다.
     *
     * @param adminId
     *            관리자 ID
     * @param notes
     *            활성화 메모
     */
    void activateSundayReservation(Long adminId, String notes);
}
