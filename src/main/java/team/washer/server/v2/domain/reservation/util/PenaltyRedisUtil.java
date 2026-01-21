package team.washer.server.v2.domain.reservation.util;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import team.washer.server.v2.domain.user.entity.User;
import team.washer.server.v2.domain.user.repository.UserRepository;

@Slf4j
@Component
@RequiredArgsConstructor
public class PenaltyRedisUtil {

    private static final String PENALTY_KEY_PREFIX = "reservation:penalty:user:";
    private static final int PENALTY_DURATION_MINUTES = 10;

    private final RedisTemplate<String, String> redisTemplate;
    private final UserRepository userRepository;

    public void applyPenalty(final User user) {
        final String redisKey = PENALTY_KEY_PREFIX + user.getId();
        final LocalDateTime expiryTime = LocalDateTime.now().plusMinutes(PENALTY_DURATION_MINUTES);

        try {
            redisTemplate.opsForValue()
                    .set(redisKey, expiryTime.toString(), PENALTY_DURATION_MINUTES, TimeUnit.MINUTES);
            log.info("Applied penalty to user {} in Redis, expires at {}", user.getId(), expiryTime);
        } catch (Exception e) {
            log.error("Failed to apply penalty in Redis, falling back to database", e);
        }
        user.updateLastCancellationTime();
        userRepository.save(user);
        log.info("Applied penalty to user {} in database, expires at {}", user.getId(), expiryTime);
    }

    public LocalDateTime getPenaltyExpiryTime(final Long userId) {
        final String redisKey = PENALTY_KEY_PREFIX + userId;

        try {
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

    public void clearPenalty(final Long userId) {
        final String redisKey = PENALTY_KEY_PREFIX + userId;

        try {
            redisTemplate.delete(redisKey);
            log.info("Cleared penalty for user {} from Redis", userId);
        } catch (Exception e) {
            log.error("Failed to clear penalty from Redis", e);
        }

        final User user = userRepository.findById(userId).orElse(null);
        if (user != null) {
            user.clearLastCancellationTime();
            userRepository.save(user);
            log.info("Cleared penalty for user {} from database", userId);
        }
    }
}
