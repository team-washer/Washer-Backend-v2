package team.washer.server.v2.domain.reservation.service.impl;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import team.washer.server.v2.domain.reservation.service.ClearUserPenaltyService;
import team.washer.server.v2.domain.reservation.util.PenaltyRedisUtil;
import team.washer.server.v2.domain.user.entity.User;
import team.washer.server.v2.domain.user.repository.UserRepository;
import team.washer.server.v2.global.common.error.exception.ExpectedException;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClearUserPenaltyServiceImpl implements ClearUserPenaltyService {

    private final UserRepository userRepository;
    private final PenaltyRedisUtil penaltyRedisUtil;

    @Override
    @Transactional
    public void execute(final Long adminId, final Long userId) {
        final User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new ExpectedException("사용자를 찾을 수 없습니다", HttpStatus.NOT_FOUND));

        if (!admin.getRole().isAdmin()) {
            log.warn("Unauthorized penalty clear attempt by user {} for user {}", adminId, userId);
            throw new ExpectedException("관리자 권한이 필요합니다", HttpStatus.FORBIDDEN);
        }

        penaltyRedisUtil.clearPenalty(userId);
        log.info("Penalty cleared for user {} by admin {}", userId, adminId);
    }
}
