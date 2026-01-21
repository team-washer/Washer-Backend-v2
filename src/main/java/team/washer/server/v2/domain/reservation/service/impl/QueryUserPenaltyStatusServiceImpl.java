package team.washer.server.v2.domain.reservation.service.impl;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import team.washer.server.v2.domain.reservation.dto.response.PenaltyStatusResDto;
import team.washer.server.v2.domain.reservation.service.QueryPenaltyStatusService;
import team.washer.server.v2.domain.reservation.service.QueryUserPenaltyStatusService;

/**
 * 사용자 패널티 상태 조회 서비스 구현체
 */
@Service
@RequiredArgsConstructor
public class QueryUserPenaltyStatusServiceImpl implements QueryUserPenaltyStatusService {

    private final QueryPenaltyStatusService queryPenaltyStatusService;

    @Override
    @Transactional(readOnly = true)
    public PenaltyStatusResDto execute(final Long userId) {
        return queryPenaltyStatusService.execute(userId);
    }
}
