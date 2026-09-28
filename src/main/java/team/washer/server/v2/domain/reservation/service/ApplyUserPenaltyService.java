package team.washer.server.v2.domain.reservation.service;

public interface ApplyUserPenaltyService {

    /**
     * 대상 사용자의 호실에 48시간 세탁 패널티를 부과합니다.
     *
     * @param userId
     *            패널티 부과 대상 사용자 ID
     * @param reason
     *            부과 사유
     */
    void execute(Long userId, String reason);
}
