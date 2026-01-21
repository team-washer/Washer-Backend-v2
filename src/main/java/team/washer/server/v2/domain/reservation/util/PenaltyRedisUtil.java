package team.washer.server.v2.domain.reservation.util;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import team.washer.server.v2.domain.reservation.entity.redis.PenaltyEntity;
import team.washer.server.v2.domain.reservation.repository.redis.PenaltyRedisRepository;
import team.washer.server.v2.domain.user.entity.User;
import team.washer.server.v2.domain.user.repository.UserRepository;
import team.washer.server.v2.global.common.constants.PenaltyConstants;

@Slf4j
@Component
@RequiredArgsConstructor
public class PenaltyRedisUtil {

    private final PenaltyRedisRepository penaltyRedisRepository;
    private final UserRepository userRepository;

    public void applyPenalty(final User user) {
        final long penaltyDurationSeconds = PenaltyConstants.PENALTY_DURATION_MINUTES * 60L;
        final LocalDateTime expiryTime = LocalDateTime.now().plusMinutes(PenaltyConstants.PENALTY_DURATION_MINUTES);

        try {
            PenaltyEntity penalty = PenaltyEntity.builder().userId(user.getId()).expiryTime(expiryTime)
                    .ttl(penaltyDurationSeconds).build();
            penaltyRedisRepository.save(penalty);
            log.info("Applied penalty to user {} in Redis, expires at {}", user.getId(), expiryTime);
        } catch (Exception e) {
            log.error("Failed to apply penalty in Redis, falling back to database", e);
        }
        user.updateLastCancellationTime();
        userRepository.save(user);
        log.info("Applied penalty to user {} in database, expires at {}", user.getId(), expiryTime);
    }

    public LocalDateTime getPenaltyExpiryTime(final Long userId) {
        try {
            Optional<LocalDateTime> redisExpiry = penaltyRedisRepository.findById(userId)
                    .map(PenaltyEntity::getExpiryTime).filter(expiryTime -> LocalDateTime.now().isBefore(expiryTime));
            if (redisExpiry.isPresent()) {
                return redisExpiry.get();
            }
        } catch (Exception e) {
            log.warn("Failed to get penalty expiry time from Redis, falling back to database", e);
        }
        final User user = userRepository.findById(userId).orElse(null);
        if (user == null || user.getLastCancellationAt() == null) {
            return null;
        }

        final LocalDateTime expiryTime = user.getLastCancellationAt()
                .plusMinutes(PenaltyConstants.PENALTY_DURATION_MINUTES);
        if (LocalDateTime.now().isBefore(expiryTime)) {
            return expiryTime;
        }

        return null;
    }

    public void clearPenalty(final Long userId) {
        try {
            penaltyRedisRepository.deleteById(userId);
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
