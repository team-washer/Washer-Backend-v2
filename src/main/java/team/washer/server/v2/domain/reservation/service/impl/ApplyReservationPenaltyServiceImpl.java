package team.washer.server.v2.domain.reservation.service.impl;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import team.washer.server.v2.domain.reservation.service.ApplyReservationPenaltyService;
import team.washer.server.v2.domain.user.entity.User;
import team.washer.server.v2.domain.user.repository.UserRepository;

/**
 * 예약 취소 패널티 적용 서비스 구현체
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApplyReservationPenaltyServiceImpl implements ApplyReservationPenaltyService {

    private static final String PENALTY_KEY_PREFIX = "reservation:penalty:user:";
    private static final int PENALTY_DURATION_MINUTES = 10;

    private final RedisTemplate<String, String> redisTemplate;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public void execute(final User user) {
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
}
