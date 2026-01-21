package team.washer.server.v2.domain.reservation.service.impl;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import team.washer.server.v2.domain.reservation.service.ActivateSundayReservationService;
import team.washer.server.v2.domain.reservation.service.PersistSundayReservationActivationService;
import team.washer.server.v2.domain.user.entity.User;
import team.washer.server.v2.domain.user.repository.UserRepository;
import team.washer.server.v2.global.common.error.exception.ExpectedException;

@Slf4j
@Service
@RequiredArgsConstructor
public class ActivateSundayReservationServiceImpl implements ActivateSundayReservationService {

    private final UserRepository userRepository;
    private final PersistSundayReservationActivationService persistSundayReservationActivationService;

    @Override
    @Transactional
    public void execute(final Long adminId, final String notes) {
        final User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new ExpectedException("사용자를 찾을 수 없습니다", HttpStatus.NOT_FOUND));

        if (!admin.getRole().canManageSundayReservation()) {
            log.warn("Unauthorized sunday activation attempt by user {}", adminId);
            throw new ExpectedException("일요일 예약 관리 권한이 없습니다", HttpStatus.FORBIDDEN);
        }

        persistSundayReservationActivationService.execute(admin, notes, true);
        log.info("Sunday reservation activated by user {}", adminId);
    }
}
