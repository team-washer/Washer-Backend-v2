package team.washer.server.v2.domain.reservation.service;

import team.washer.server.v2.domain.user.entity.User;

/**
 * 예약 취소 패널티 적용 서비스
 */
public interface ApplyReservationPenaltyService {

    /**
     * 예약 취소 패널티 적용
     *
     * @param user
     *            패널티를 적용할 사용자
     */
    void execute(User user);
}
