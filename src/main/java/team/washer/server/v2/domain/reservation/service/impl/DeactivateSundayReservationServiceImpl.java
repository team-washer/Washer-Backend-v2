package team.washer.server.v2.domain.reservation.service.impl;

import java.util.Objects;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import team.themoment.sdk.exception.ExpectedException;
import team.washer.server.v2.domain.reservation.service.DeactivateSundayReservationService;
import team.washer.server.v2.domain.reservation.util.SundayReservationRedisUtil;
import team.washer.server.v2.domain.user.entity.User;
import team.washer.server.v2.domain.user.repository.UserRepository;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeactivateSundayReservationServiceImpl implements DeactivateSundayReservationService {

    private final UserRepository userRepository;
    private final SundayReservationRedisUtil sundayReservationRedisUtil;

    @Override
    @Transactional
    public void execute(final String notes) {
        final var adminId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        final User admin = userRepository.findById(Objects.requireNonNull(adminId))
                .orElseThrow(() -> new ExpectedException("사용자를 찾을 수 없습니다", HttpStatus.NOT_FOUND));

        if (!admin.getRole().canManageSundayReservation()) {
            log.warn("Unauthorized sunday deactivation attempt by user {}", adminId);
            throw new ExpectedException("일요일 예약 관리 권한이 없습니다", HttpStatus.FORBIDDEN);
        }

        sundayReservationRedisUtil.persistActivation(admin, notes, false);
        log.info("Sunday reservation deactivated by user {}", adminId);
    }
}
