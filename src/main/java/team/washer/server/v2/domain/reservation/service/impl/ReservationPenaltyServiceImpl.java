package team.washer.server.v2.domain.reservation.service.impl;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import team.washer.server.v2.domain.reservation.service.ReservationPenaltyService;
import team.washer.server.v2.domain.user.entity.User;
import team.washer.server.v2.domain.user.repository.UserRepository;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReservationPenaltyServiceImpl implements ReservationPenaltyService {

    private static final String PENALTY_KEY_PREFIX = "reservation:penalty:user:";
    private static final int PENALTY_DURATION_MINUTES = 10;

    private final RedisTemplate<String, String> redisTemplate;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public void applyPenalty(User user) {
        String redisKey = PENALTY_KEY_PREFIX + user.getId();
        LocalDateTime expiryTime = LocalDateTime.now().plusMinutes(PENALTY_DURATION_MINUTES);

        try {
            // Redis에 패널티 저장 (TTL 10분)
            redisTemplate.opsForValue().set(redisKey, expiryTime.toString(), PENALTY_DURATION_MINUTES,
                    TimeUnit.MINUTES);
            log.info("Applied penalty to user {} in Redis, expires at {}", user.getId(), expiryTime);
        } catch (Exception e) {
            log.error("Failed to apply penalty in Redis, falling back to database", e);
        }

        // DB에도 백업 (Redis 장애 시 폴백용)
        user.updateLastCancellationTime();
        userRepository.save(user);
        log.info("Applied penalty to user {} in database, expires at {}", user.getId(), expiryTime);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isPenalized(Long userId) {
        String redisKey = PENALTY_KEY_PREFIX + userId;

        try {
            // Redis 우선 확인
            String expiryTimeStr = redisTemplate.opsForValue().get(redisKey);
            if (expiryTimeStr != null) {
                LocalDateTime expiryTime = LocalDateTime.parse(expiryTimeStr);
                boolean isPenalized = LocalDateTime.now().isBefore(expiryTime);
                log.debug("Checked penalty for user {} in Redis: {}", userId, isPenalized);
                return isPenalized;
            }
        } catch (Exception e) {
            log.warn("Failed to check penalty in Redis, falling back to database", e);
        }

        // Redis 실패 시 DB 폴백
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return false;
        }

        boolean isPenalized = user.hasRecentCancellation(PENALTY_DURATION_MINUTES);
        log.debug("Checked penalty for user {} in database: {}", userId, isPenalized);
        return isPenalized;
    }

    @Override
    @Transactional(readOnly = true)
    public LocalDateTime getPenaltyExpiryTime(Long userId) {
        String redisKey = PENALTY_KEY_PREFIX + userId;

        try {
            // Redis 우선 확인
            String expiryTimeStr = redisTemplate.opsForValue().get(redisKey);
            if (expiryTimeStr != null) {
                LocalDateTime expiryTime = LocalDateTime.parse(expiryTimeStr);
                if (LocalDateTime.now().isBefore(expiryTime)) {
                    return expiryTime;
                }
            }
        } catch (Exception e) {
            log.warn("Failed to get penalty expiry time from Redis, falling back to database", e);
        }

        // Redis 실패 시 DB 폴백
        User user = userRepository.findById(userId).orElse(null);
        if (user == null || user.getLastCancellationAt() == null) {
            return null;
        }

        LocalDateTime expiryTime = user.getLastCancellationAt().plusMinutes(PENALTY_DURATION_MINUTES);
        if (LocalDateTime.now().isBefore(expiryTime)) {
            return expiryTime;
        }

        return null;
    }

    @Override
    @Transactional
    public void clearPenalty(Long userId) {
        String redisKey = PENALTY_KEY_PREFIX + userId;

        try {
            // Redis에서 제거
            redisTemplate.delete(redisKey);
            log.info("Cleared penalty for user {} from Redis", userId);
        } catch (Exception e) {
            log.error("Failed to clear penalty from Redis", e);
        }

        // DB에서도 제거
        User user = userRepository.findById(userId).orElse(null);
        if (user != null) {
            user.clearLastCancellationTime();
            userRepository.save(user);
            log.info("Cleared penalty for user {} from database", userId);
        }
    }
}
