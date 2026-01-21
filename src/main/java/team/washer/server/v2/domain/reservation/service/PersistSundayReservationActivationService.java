package team.washer.server.v2.domain.reservation.service;

import team.washer.server.v2.domain.user.entity.User;

/**
 * 일요일 예약 활성화/비활성화 영속화 서비스
 */
public interface PersistSundayReservationActivationService {

    /**
     * 일요일 예약 활성화/비활성화 영속화
     *
     * @param performedBy
     *            작업을 수행한 사용자
     * @param notes
     *            작업 메모
     * @param isActive
     *            활성화 여부 (true: 활성화, false: 비활성화)
     */
    void execute(User performedBy, String notes, boolean isActive);
}
