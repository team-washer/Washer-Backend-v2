package team.washer.server.v2.domain.reservation.service;

import team.washer.server.v2.domain.reservation.dto.response.PenaltyStatusResDto;

/**
 * 패널티 상태 조회 서비스
 */
public interface QueryPenaltyStatusService {

    /**
     * 패널티 상태 조회
     *
     * @param userId
     *            사용자 ID
     * @return 패널티 상태 정보
     */
    PenaltyStatusResDto execute(Long userId);
}
