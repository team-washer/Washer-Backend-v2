package team.washer.server.v2.domain.reservation.service;

/**
 * 사용자 패널티 상태 검증 서비스
 */
public interface ValidateUserNotPenalizedService {

    /**
     * 사용자 패널티 상태 검증
     *
     * @param userId
     *            사용자 ID
     * @throws IllegalStateException
     *             패널티 상태일 때
     */
    void execute(Long userId);
}
