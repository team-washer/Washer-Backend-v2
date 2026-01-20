package team.washer.server.v2.domain.reservation.service;

import team.washer.server.v2.domain.reservation.dto.response.PenaltyStatusResDto;

public interface QueryUserPenaltyStatusService {

    /**
     * 사용자 패널티 상태를 조회합니다.
     *
     * @param userId
     *            사용자 ID
     * @return 패널티 상태 DTO
     */
    PenaltyStatusResDto execute(Long userId);
}
