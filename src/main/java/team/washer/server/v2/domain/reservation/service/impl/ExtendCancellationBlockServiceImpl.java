package team.washer.server.v2.domain.reservation.service.impl;

import java.time.LocalDateTime;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import team.themoment.sdk.exception.ExpectedException;
import team.washer.server.v2.domain.notification.support.ReservationNotificationSupport;
import team.washer.server.v2.domain.reservation.service.ExtendCancellationBlockService;
import team.washer.server.v2.domain.reservation.util.PenaltyRedisUtil;
import team.washer.server.v2.domain.user.entity.User;
import team.washer.server.v2.domain.user.repository.UserRepository;
import team.washer.server.v2.global.security.provider.CurrentUserProvider;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExtendCancellationBlockServiceImpl implements ExtendCancellationBlockService {

    private final UserRepository userRepository;
    private final PenaltyRedisUtil penaltyRedisUtil;
    private final ReservationNotificationSupport reservationNotificationSupport;
    private final CurrentUserProvider currentUserProvider;

    @Override
    @Transactional
    public void execute(final Long userId, final int days) {
        final var adminId = currentUserProvider.getCurrentUserId();
        final User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new ExpectedException("사용자를 찾을 수 없습니다.", HttpStatus.NOT_FOUND));

        if (!admin.getRole().isAdmin()) {
            log.warn("Unauthorized block extend attempt by user {} for user {}", adminId, userId);
            throw new ExpectedException("관리자 권한이 필요합니다.", HttpStatus.FORBIDDEN);
        }

        final User target = userRepository.findById(userId)
                .orElseThrow(() -> new ExpectedException("사용자를 찾을 수 없습니다.", HttpStatus.NOT_FOUND));

        final String roomNumber = target.getRoomNumber();
        if (roomNumber == null) {
            throw new ExpectedException("호실 정보를 찾을 수 없습니다.", HttpStatus.NOT_FOUND);
        }

        final LocalDateTime newExpiryAt = penaltyRedisUtil.extendBlock(roomNumber, days);
        log.info("block extended roomNumber={} additionalDays={} newExpiry={} by admin {}",
                roomNumber,
                days,
                newExpiryAt,
                adminId);

        reservationNotificationSupport.sendBlockExtension(target, newExpiryAt);
    }
}
