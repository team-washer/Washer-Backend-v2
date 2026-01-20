package team.washer.server.v2.domain.reservation.service.impl;

import java.time.Duration;
import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import team.washer.server.v2.domain.reservation.dto.response.PenaltyStatusResDto;
import team.washer.server.v2.domain.reservation.service.QueryUserPenaltyStatusService;
import team.washer.server.v2.domain.reservation.service.ReservationPenaltyService;

@Service
@RequiredArgsConstructor
public class QueryUserPenaltyStatusServiceImpl implements QueryUserPenaltyStatusService {

    private final ReservationPenaltyService penaltyService;

    @Override
    @Transactional(readOnly = true)
    public PenaltyStatusResDto queryUserPenaltyStatus(final Long userId) {
        final boolean isPenalized = penaltyService.isPenalized(userId);
        final LocalDateTime penaltyExpiresAt = penaltyService.getPenaltyExpiryTime(userId);

        Long remainingMinutes = null;
        if (isPenalized && penaltyExpiresAt != null) {
            remainingMinutes = Duration.between(LocalDateTime.now(), penaltyExpiresAt).toMinutes();
        }

        return new PenaltyStatusResDto(userId, isPenalized, penaltyExpiresAt, remainingMinutes);
    }
}
