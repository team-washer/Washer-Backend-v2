package team.washer.server.v2.domain.reservation.util;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import team.washer.server.v2.domain.reservation.entity.redis.CancellationBlockEntity;
import team.washer.server.v2.domain.reservation.entity.redis.CooldownEntity;
import team.washer.server.v2.domain.reservation.entity.redis.PenaltyEntity;
import team.washer.server.v2.domain.reservation.entity.redis.TimeoutWarningEntity;
import team.washer.server.v2.domain.reservation.repository.redis.CancellationBlockRedisRepository;
import team.washer.server.v2.domain.reservation.repository.redis.CooldownRedisRepository;
import team.washer.server.v2.domain.reservation.repository.redis.PenaltyRedisRepository;
import team.washer.server.v2.domain.reservation.repository.redis.TimeoutWarningRedisRepository;
import team.washer.server.v2.domain.user.entity.User;
import team.washer.server.v2.domain.user.repository.UserRepository;
import team.washer.server.v2.global.common.constants.PenaltyConstants;

@Slf4j
@Component
@RequiredArgsConstructor
public class PenaltyRedisUtil {

    private final PenaltyRedisRepository penaltyRedisRepository;
    private final CooldownRedisRepository cooldownRedisRepository;
    private final TimeoutWarningRedisRepository timeoutWarningRedisRepository;
    private final CancellationBlockRedisRepository cancellationBlockRedisRepository;
    private final StringRedisTemplate stringRedisTemplate;
    private final UserRepository userRepository;

    // ===== 기존 패널티 (하위 호환) =====

    public void applyPenalty(final User user) {
        final long penaltyDurationSeconds = PenaltyConstants.PENALTY_DURATION_MINUTES * 60L;
        final LocalDateTime expiryTime = LocalDateTime.now().plusMinutes(PenaltyConstants.PENALTY_DURATION_MINUTES);

        try {
            var penalty = PenaltyEntity.builder().userId(user.getId()).expiryTime(expiryTime)
                    .ttl(penaltyDurationSeconds).build();
            penaltyRedisRepository.save(penalty);
        } catch (Exception e) {
            log.error("Failed to apply penalty in Redis, falling back to database", e);
        }
        user.updateLastCancellationTime();
        userRepository.save(user);
    }

    public LocalDateTime getPenaltyExpiryTime(final Long userId) {
        try {
            Optional<LocalDateTime> redisExpiry = penaltyRedisRepository.findById(userId)
                    .map(PenaltyEntity::getExpiryTime).filter(t -> LocalDateTime.now().isBefore(t));
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
        return LocalDateTime.now().isBefore(expiryTime) ? expiryTime : null;
    }

    public void clearPenalty(final Long userId) {
        try {
            penaltyRedisRepository.deleteById(userId);
        } catch (Exception e) {
            log.error("Failed to clear penalty from Redis", e);
        }
        final User user = userRepository.findById(userId).orElse(null);
        if (user != null) {
            user.clearLastCancellationTime();
            userRepository.save(user);
        }
    }

    // ===== 5분 쿨다운 =====

    /**
     * 취소 직후 5분 재예약 쿨다운을 적용합니다.
     */
    public void applyCooldown(final Long userId) {
        try {
            final long ttlSeconds = PenaltyConstants.COOLDOWN_DURATION_MINUTES * 60L;
            cooldownRedisRepository.save(CooldownEntity.builder().userId(userId).ttl(ttlSeconds).build());
            log.info("쿨다운 적용 - 사용자: {}, 만료: {}분 후", userId, PenaltyConstants.COOLDOWN_DURATION_MINUTES);
        } catch (Exception e) {
            log.error("쿨다운 적용 실패 - 사용자: {}", userId, e);
        }
    }

    /**
     * 현재 쿨다운 중인지 여부를 반환합니다.
     */
    public boolean isInCooldown(final Long userId) {
        try {
            return cooldownRedisRepository.existsById(userId);
        } catch (Exception e) {
            log.warn("쿨다운 조회 실패 - 사용자: {}", userId, e);
            return false;
        }
    }

    // ===== 타임아웃 경고 (첫 번째) =====

    /**
     * 첫 번째 타임아웃 경고를 기록합니다 (TTL 7일).
     */
    public void applyWarning(final Long userId) {
        try {
            final long ttlSeconds = PenaltyConstants.WARNING_DURATION_DAYS * 24L * 3600L;
            timeoutWarningRedisRepository.save(TimeoutWarningEntity.builder().userId(userId).ttl(ttlSeconds).build());
            log.info("타임아웃 경고 기록 - 사용자: {}", userId);
        } catch (Exception e) {
            log.error("타임아웃 경고 기록 실패 - 사용자: {}", userId, e);
        }
    }

    /**
     * 이미 타임아웃 경고가 있는지 여부를 반환합니다.
     */
    public boolean hasWarning(final Long userId) {
        try {
            return timeoutWarningRedisRepository.existsById(userId);
        } catch (Exception e) {
            log.warn("경고 조회 실패 - 사용자: {}", userId, e);
            return false;
        }
    }

    // ===== 48시간 취소 횟수 (슬라이딩 윈도우) =====

    /**
     * 취소 이력에 현재 시각을 기록하고 48시간 이전 항목을 제거합니다.
     */
    public void recordCancellation(final Long userId) {
        try {
            var key = PenaltyConstants.CANCEL_HISTORY_KEY_PREFIX + userId;
            long nowMillis = Instant.now().toEpochMilli();
            long windowStart = nowMillis - PenaltyConstants.CANCELLATION_WINDOW_HOURS * 3600L * 1000L;

            stringRedisTemplate.opsForZSet().add(key, String.valueOf(nowMillis), nowMillis);
            stringRedisTemplate.opsForZSet().removeRangeByScore(key, Double.NEGATIVE_INFINITY, windowStart);
            // TTL: 윈도우 크기 + 여유 1시간
            stringRedisTemplate.expire(key, java.time.Duration.ofHours(PenaltyConstants.CANCELLATION_WINDOW_HOURS + 1));
            log.info("취소 이력 기록 - 사용자: {}", userId);
        } catch (Exception e) {
            log.error("취소 이력 기록 실패 - 사용자: {}", userId, e);
        }
    }

    /**
     * 최근 48시간 내 취소 횟수를 반환합니다.
     */
    public long getCancellationCount(final Long userId) {
        try {
            var key = PenaltyConstants.CANCEL_HISTORY_KEY_PREFIX + userId;
            long windowStart = Instant.now().toEpochMilli()
                    - PenaltyConstants.CANCELLATION_WINDOW_HOURS * 3600L * 1000L;
            Long count = stringRedisTemplate.opsForZSet().count(key, windowStart, Double.POSITIVE_INFINITY);
            return count != null ? count : 0L;
        } catch (Exception e) {
            log.warn("취소 횟수 조회 실패 - 사용자: {}", userId, e);
            return 0L;
        }
    }

    // ===== 48시간 예약 차단 =====

    /**
     * 48시간 예약 차단을 적용합니다.
     */
    public void applyBlock(final Long userId) {
        try {
            final long ttlSeconds = PenaltyConstants.CANCELLATION_WINDOW_HOURS * 3600L;
            cancellationBlockRedisRepository
                    .save(CancellationBlockEntity.builder().userId(userId).ttl(ttlSeconds).build());
            log.info("48시간 예약 차단 적용 - 사용자: {}", userId);
        } catch (Exception e) {
            log.error("48시간 예약 차단 적용 실패 - 사용자: {}", userId, e);
        }
    }

    /**
     * 현재 48시간 예약 차단 중인지 여부를 반환합니다.
     */
    public boolean isBlocked(final Long userId) {
        try {
            return cancellationBlockRedisRepository.existsById(userId);
        } catch (Exception e) {
            log.warn("차단 조회 실패 - 사용자: {}", userId, e);
            return false;
        }
    }
}
