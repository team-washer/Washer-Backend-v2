package team.washer.server.v2.domain.reservation.service.impl;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import team.washer.server.v2.domain.reservation.service.ClearUserPenaltyService;
import team.washer.server.v2.domain.user.entity.User;
import team.washer.server.v2.domain.user.repository.UserRepository;
import team.washer.server.v2.global.common.error.exception.ExpectedException;

/**
 * 사용자 패널티 해제 서비스 구현체
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClearUserPenaltyServiceImpl implements ClearUserPenaltyService {

    private static final String PENALTY_KEY_PREFIX = "reservation:penalty:user:";

    private final UserRepository userRepository;
    private final RedisTemplate<String, String> redisTemplate;

    @Override
    @Transactional
    public void execute(final Long adminId, final Long userId) {
        final User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new ExpectedException("사용자를 찾을 수 없습니다", HttpStatus.NOT_FOUND));

        if (!admin.getRole().isAdmin()) {
            log.warn("Unauthorized penalty clear attempt by user {} for user {}", adminId, userId);
            throw new ExpectedException("관리자 권한이 필요합니다", HttpStatus.FORBIDDEN);
        }

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

        log.info("Penalty cleared for user {} by admin {}", userId, adminId);
    }
}
