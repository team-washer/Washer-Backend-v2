package team.washer.server.v2.domain.reservation.service.impl;

import java.time.Duration;
import java.time.LocalDateTime;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import team.washer.server.v2.domain.reservation.dto.response.PenaltyStatusResDto;
import team.washer.server.v2.domain.reservation.service.QueryPenaltyStatusService;
import team.washer.server.v2.domain.user.entity.User;
import team.washer.server.v2.domain.user.repository.UserRepository;

/**
 * 패널티 상태 조회 서비스 구현체
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QueryPenaltyStatusServiceImpl implements QueryPenaltyStatusService {

    private static final String PENALTY_KEY_PREFIX = "reservation:penalty:user:";
    private static final int PENALTY_DURATION_MINUTES = 10;

    private final RedisTemplate<String, String> redisTemplate;
    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public PenaltyStatusResDto execute(final Long userId) {
        final LocalDateTime penaltyExpiresAt = getPenaltyExpiryTime(userId);
        final boolean isPenalized = penaltyExpiresAt != null && LocalDateTime.now().isBefore(penaltyExpiresAt);

        Long remainingMinutes = null;
        if (isPenalized && penaltyExpiresAt != null) {
            remainingMinutes = Duration.between(LocalDateTime.now(), penaltyExpiresAt).toMinutes();
        }

        return new PenaltyStatusResDto(userId, isPenalized, penaltyExpiresAt, remainingMinutes);
    }

    /**
     * 패널티 만료 시간 조회 (내부 메서드)
     *
     * @param userId
     *            사용자 ID
     * @return 패널티 만료 시간 (패널티가 없으면 null)
     */
    private LocalDateTime getPenaltyExpiryTime(final Long userId) {
        final String redisKey = PENALTY_KEY_PREFIX + userId;

        try {
            // Redis 우선 확인
            final String expiryTimeStr = redisTemplate.opsForValue().get(redisKey);
            if (expiryTimeStr != null) {
                final LocalDateTime expiryTime = LocalDateTime.parse(expiryTimeStr);
                if (LocalDateTime.now().isBefore(expiryTime)) {
                    return expiryTime;
                }
            }
        } catch (Exception e) {
            log.warn("Failed to get penalty expiry time from Redis, falling back to database", e);
        }

        // Redis 실패 시 DB 폴백
        final User user = userRepository.findById(userId).orElse(null);
        if (user == null || user.getLastCancellationAt() == null) {
            return null;
        }

        final LocalDateTime expiryTime = user.getLastCancellationAt().plusMinutes(PENALTY_DURATION_MINUTES);
        if (LocalDateTime.now().isBefore(expiryTime)) {
            return expiryTime;
        }

        return null;
    }
}
