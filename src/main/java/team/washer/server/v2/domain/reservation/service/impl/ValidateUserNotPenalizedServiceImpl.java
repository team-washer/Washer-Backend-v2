package team.washer.server.v2.domain.reservation.service.impl;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import team.washer.server.v2.domain.reservation.dto.response.PenaltyStatusResDto;
import team.washer.server.v2.domain.reservation.service.QueryPenaltyStatusService;
import team.washer.server.v2.domain.reservation.service.ValidateUserNotPenalizedService;

/**
 * 사용자 패널티 상태 검증 서비스 구현체
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ValidateUserNotPenalizedServiceImpl implements ValidateUserNotPenalizedService {

    private final QueryPenaltyStatusService queryPenaltyStatusService;

    @Override
    @Transactional(readOnly = true)
    public void execute(final Long userId) {
        final PenaltyStatusResDto penaltyStatus = queryPenaltyStatusService.execute(userId);
        if (penaltyStatus.isPenalized()) {
            throw new IllegalStateException(
                    String.format("현재 예약이 제한되어 있습니다. 제한 해제 시간: %s", penaltyStatus.penaltyExpiresAt()));
        }
        log.debug("Penalty validation passed for user {}", userId);
    }
}
