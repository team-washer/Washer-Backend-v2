package team.washer.server.v2.domain.reservation.service.impl;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import team.washer.server.v2.domain.reservation.service.ActivateSundayReservationService;
import team.washer.server.v2.domain.reservation.service.SundayReservationService;
import team.washer.server.v2.domain.user.entity.User;
import team.washer.server.v2.domain.user.repository.UserRepository;

@Slf4j
@Service
@RequiredArgsConstructor
public class ActivateSundayReservationServiceImpl implements ActivateSundayReservationService {

    private final UserRepository userRepository;
    private final SundayReservationService sundayReservationService;

    @Override
    @Transactional
    public void activateSundayReservation(final Long adminId, final String notes) {
        final User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다: " + adminId));

        if (!admin.getRole().canManageSundayReservation()) {
            log.warn("Unauthorized sunday activation attempt by user {}", adminId);
            throw new IllegalArgumentException("일요일 예약 관리 권한이 없습니다");
        }

        sundayReservationService.activateSundayReservation(admin, notes);
        log.info("Sunday reservation activated by user {}", adminId);
    }
}
