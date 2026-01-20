package team.washer.server.v2.domain.reservation.service;

import java.time.LocalDateTime;

import team.washer.server.v2.domain.user.entity.User;

public interface ReservationPenaltyService {

    /**
     * 예약 취소 패널티 적용
     *
     * @param user 패널티를 적용할 사용자
     */
    void applyPenalty(User user);

    /**
     * 패널티 상태 확인
     *
     * @param userId 사용자 ID
     * @return 패널티가 활성화되어 있으면 true
     */
    boolean isPenalized(Long userId);

    /**
     * 패널티 만료 시간 조회
     *
     * @param userId 사용자 ID
     * @return 패널티 만료 시간 (패널티가 없으면 null)
     */
    LocalDateTime getPenaltyExpiryTime(Long userId);

    /**
     * 패널티 해제 (관리자용)
     *
     * @param userId 사용자 ID
     */
    void clearPenalty(Long userId);
}
