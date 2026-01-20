package team.washer.server.v2.domain.reservation.service;

public interface DeactivateSundayReservationService {

    /**
     * 일요일 예약을 비활성화합니다.
     *
     * @param adminId
     *            관리자 ID
     * @param notes
     *            비활성화 메모
     */
    void deactivateSundayReservation(Long adminId, String notes);
}
