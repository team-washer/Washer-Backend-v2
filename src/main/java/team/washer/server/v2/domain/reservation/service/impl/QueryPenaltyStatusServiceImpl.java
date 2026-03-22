package team.washer.server.v2.domain.reservation.service.impl;

import java.time.Duration;
import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import team.washer.server.v2.domain.reservation.dto.response.PenaltyStatusResDto;
import team.washer.server.v2.domain.reservation.service.QueryPenaltyStatusService;
import team.washer.server.v2.domain.reservation.util.PenaltyRedisUtil;

@Slf4j
@Service
@RequiredArgsConstructor
public class QueryPenaltyStatusServiceImpl implements QueryPenaltyStatusService {

    private final PenaltyRedisUtil penaltyRedisUtil;

    @Override
    @Transactional(readOnly = true)
    public PenaltyStatusResDto execute(final Long userId) {
        final LocalDateTime penaltyExpiresAt = penaltyRedisUtil.getPenaltyExpiryTime(userId);
        final boolean isPenalized = penaltyExpiresAt != null && LocalDateTime.now().isBefore(penaltyExpiresAt);

        Long remainingMinutes = null;
        if (isPenalized) {
            remainingMinutes = Duration.between(LocalDateTime.now(), penaltyExpiresAt).toMinutes();
        }

        return new PenaltyStatusResDto(userId, isPenalized, penaltyExpiresAt, remainingMinutes);
    }
}
