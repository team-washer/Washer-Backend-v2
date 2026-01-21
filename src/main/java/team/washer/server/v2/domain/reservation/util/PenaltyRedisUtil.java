package team.washer.server.v2.domain.reservation.util;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import team.washer.server.v2.domain.user.entity.User;
import team.washer.server.v2.domain.user.repository.UserRepository;

/**
 * 예약 취소 패널티 관련 Redis 작업을 처리하는 유틸리티 컴포넌트
 *
 * <p>
 * Redis를 우선적으로 사용하며, 장애 시 데이터베이스로 폴백한다. 모든 패널티 작업은 Redis와 DB 양쪽에 동기화된다.
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PenaltyRedisUtil {

    private static final String PENALTY_KEY_PREFIX = "reservation:penalty:user:";
    private static final int PENALTY_DURATION_MINUTES = 10;

    private final RedisTemplate<String, String> redisTemplate;
    private final UserRepository userRepository;

    /**
     * 사용자에게 예약 취소 패널티를 적용
     *
     * <p>
     * Redis에 TTL 10분으로 패널티 정보를 저장하며, 동시에 DB에도 백업한다.
     * </p>
     *
     * @param user
     *            패널티를 적용할 사용자 엔티티
     */
    public void applyPenalty(final User user) {
        final String redisKey = PENALTY_KEY_PREFIX + user.getId();
        final LocalDateTime expiryTime = LocalDateTime.now().plusMinutes(PENALTY_DURATION_MINUTES);

        try {
            // Redis에 패널티 저장 (TTL 10분)
            redisTemplate.opsForValue()
                    .set(redisKey, expiryTime.toString(), PENALTY_DURATION_MINUTES, TimeUnit.MINUTES);
            log.info("Applied penalty to user {} in Redis, expires at {}", user.getId(), expiryTime);
        } catch (Exception e) {
            log.error("Failed to apply penalty in Redis, falling back to database", e);
        }

        // DB에도 백업 (Redis 장애 시 폴백용)
        user.updateLastCancellationTime();
        userRepository.save(user);
        log.info("Applied penalty to user {} in database, expires at {}", user.getId(), expiryTime);
    }

    /**
     * 사용자의 패널티 만료 시간을 조회
     *
     * <p>
     * Redis를 우선적으로 확인하고, 실패 시 데이터베이스로 폴백한다. 패널티가 없거나 만료된 경우 null을 반환한다.
     * </p>
     *
     * @param userId
     *            조회할 사용자 ID
     * @return 패널티 만료 시간 (패널티가 없으면 null)
     */
    public LocalDateTime getPenaltyExpiryTime(final Long userId) {
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

    /**
     * 사용자의 패널티를 해제
     *
     * <p>
     * Redis와 데이터베이스 양쪽에서 패널티 정보를 제거한다.
     * </p>
     *
     * @param userId
     *            패널티를 해제할 사용자 ID
     */
    public void clearPenalty(final Long userId) {
        final String redisKey = PENALTY_KEY_PREFIX + userId;

        try {
            // Redis에서 제거
            redisTemplate.delete(redisKey);
            log.info("Cleared penalty for user {} from Redis", userId);
        } catch (Exception e) {
            log.error("Failed to clear penalty from Redis", e);
        }

        // DB에서도 제거
        final User user = userRepository.findById(userId).orElse(null);
        if (user != null) {
            user.clearLastCancellationTime();
            userRepository.save(user);
            log.info("Cleared penalty for user {} from database", userId);
        }
    }
}
