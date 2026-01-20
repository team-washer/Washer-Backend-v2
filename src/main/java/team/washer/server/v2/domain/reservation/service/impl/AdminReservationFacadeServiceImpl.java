package team.washer.server.v2.domain.reservation.service.impl;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import team.washer.server.v2.domain.reservation.dto.SundayActivationDto;
import team.washer.server.v2.domain.reservation.dto.SundayStatusDto;
import team.washer.server.v2.domain.reservation.service.AdminReservationFacadeService;
import team.washer.server.v2.domain.reservation.service.ReservationPenaltyService;
import team.washer.server.v2.domain.reservation.service.SundayReservationService;
import team.washer.server.v2.domain.user.entity.User;
import team.washer.server.v2.domain.user.repository.UserRepository;

/**
 * 관리자 예약 관리 Facade 서비스 구현체.
 *
 * <p>관리자 권한 검증과 도메인 서비스 조율을 담당합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminReservationFacadeServiceImpl implements AdminReservationFacadeService {

    private final UserRepository userRepository;
    private final SundayReservationService sundayReservationService;
    private final ReservationPenaltyService penaltyService;

    /**
     * 일요일 예약을 활성화합니다.
     *
     * <p>DORMITORY_COUNCIL 또는 ADMIN 권한이 필요합니다.
     *
     * @param adminId 관리자 ID
     * @param notes 활성화 메모
     * @throws IllegalArgumentException 사용자를 찾을 수 없거나 권한이 없는 경우
     */
    @Override
    @Transactional
    public void activateSundayReservation(final Long adminId, final String notes) {
        // 1. 사용자 조회
        final User admin = userRepository
                .findById(adminId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다: " + adminId));

        // 2. 권한 검증 (비즈니스 로직)
        if (!admin.getRole().canManageSundayReservation()) {
            log.warn("Unauthorized sunday activation attempt by user {}", adminId);
            throw new IllegalArgumentException("일요일 예약 관리 권한이 없습니다");
        }

        // 3. 비즈니스 로직 실행
        sundayReservationService.activateSundayReservation(admin, notes);
        log.info("Sunday reservation activated by user {}", adminId);
    }

    /**
     * 일요일 예약을 비활성화합니다.
     *
     * <p>DORMITORY_COUNCIL 또는 ADMIN 권한이 필요합니다.
     *
     * @param adminId 관리자 ID
     * @param notes 비활성화 메모
     * @throws IllegalArgumentException 사용자를 찾을 수 없거나 권한이 없는 경우
     */
    @Override
    @Transactional
    public void deactivateSundayReservation(final Long adminId, final String notes) {
        // 1. 사용자 조회
        final User admin = userRepository
                .findById(adminId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다: " + adminId));

        // 2. 권한 검증 (비즈니스 로직)
        if (!admin.getRole().canManageSundayReservation()) {
            log.warn("Unauthorized sunday deactivation attempt by user {}", adminId);
            throw new IllegalArgumentException("일요일 예약 관리 권한이 없습니다");
        }

        // 3. 비즈니스 로직 실행
        sundayReservationService.deactivateSundayReservation(admin, notes);
        log.info("Sunday reservation deactivated by user {}", adminId);
    }

    /**
     * 일요일 예약 상태를 조회합니다.
     *
     * @return 일요일 예약 상태 및 히스토리
     */
    @Override
    @Transactional(readOnly = true)
    public SundayStatusDto getSundayReservationStatus() {
        final boolean isActive = sundayReservationService.isSundayReservationActive();
        final List<SundayActivationDto> history = sundayReservationService.getSundayReservationHistory().stream()
                .limit(10)
                .map(SundayActivationDto::from)
                .collect(Collectors.toList());

        return SundayStatusDto.of(isActive, history);
    }

    /**
     * 사용자의 패널티를 해제합니다.
     *
     * <p>ADMIN 권한이 필요합니다.
     *
     * @param adminId 관리자 ID
     * @param userId 패널티를 해제할 사용자 ID
     * @throws IllegalArgumentException 관리자를 찾을 수 없거나 권한이 없는 경우
     */
    @Override
    @Transactional
    public void clearUserPenalty(final Long adminId, final Long userId) {
        // 1. 사용자 조회
        final User admin = userRepository
                .findById(adminId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다: " + adminId));

        // 2. 권한 검증 (비즈니스 로직)
        if (!admin.getRole().isAdmin()) {
            log.warn("Unauthorized penalty clear attempt by user {} for user {}", adminId, userId);
            throw new IllegalArgumentException("관리자 권한이 필요합니다");
        }

        // 3. 비즈니스 로직 실행
        penaltyService.clearPenalty(userId);
        log.info("Penalty cleared for user {} by admin {}", userId, adminId);
    }
}
