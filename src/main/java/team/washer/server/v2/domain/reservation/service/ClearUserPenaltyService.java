package team.washer.server.v2.domain.reservation.service;

public interface ClearUserPenaltyService {

    /**
     * 사용자 패널티를 해제합니다.
     *
     * @param adminId
     *            관리자 ID
     * @param userId
     *            패널티를 해제할 사용자 ID
     */
    void clearUserPenalty(Long adminId, Long userId);
}
