package team.washer.server.v2.domain.reservation.service.impl;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import team.washer.server.v2.domain.reservation.dto.response.ReservationAvailabilityResDto;
import team.washer.server.v2.domain.reservation.service.QueryReservationAvailabilityService;
import team.washer.server.v2.domain.reservation.util.PenaltyRedisUtil;
import team.washer.server.v2.global.security.provider.CurrentUserProvider;

@Service
@RequiredArgsConstructor
public class QueryReservationAvailabilityServiceImpl implements QueryReservationAvailabilityService {

    private final PenaltyRedisUtil penaltyRedisUtil;
    private final CurrentUserProvider currentUserProvider;

    @Override
    @Transactional(readOnly = true)
    public ReservationAvailabilityResDto execute() {
        final var userId = currentUserProvider.getCurrentUserId();
        final LocalDateTime penaltyExpiresAt = penaltyRedisUtil.getPenaltyExpiryTime(userId);
        final boolean canReserve = penaltyExpiresAt == null || LocalDateTime.now().isAfter(penaltyExpiresAt);

        return new ReservationAvailabilityResDto(canReserve, canReserve ? null : penaltyExpiresAt);
    }
}
