package team.washer.server.v2.global.security.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import team.washer.server.v2.domain.reservation.entity.Reservation;
import team.washer.server.v2.domain.reservation.repository.ReservationRepository;
import team.washer.server.v2.domain.user.entity.User;
import team.washer.server.v2.domain.user.enums.UserRole;
import team.washer.server.v2.domain.user.repository.UserRepository;

/**
 * 예약 시스템 권한 검증 서비스
 * Spring Security의 @PreAuthorize와 함께 사용됩니다
 */
@Service("reservationSecurity")
@RequiredArgsConstructor
public class ReservationSecurityService {

    private final ReservationRepository reservationRepository;
    private final UserRepository userRepository;

    /**
     * 사용자가 예약을 수정/취소할 권한이 있는지 확인
     * - 본인의 예약이거나
     * - DORMITORY_COUNCIL 이상의 권한이 있어야 함
     *
     * @param userId 사용자 ID
     * @param reservationId 예약 ID
     * @return 권한이 있으면 true
     */
    @Transactional(readOnly = true)
    public boolean canModifyReservation(Long userId, Long reservationId) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return false;
        }

        // DORMITORY_COUNCIL 이상은 모든 예약 수정 가능
        if (user.getRole().canBypassTimeRestrictions()) {
            return true;
        }

        // 본인 예약인지 확인
        Reservation reservation = reservationRepository.findById(reservationId).orElse(null);
        if (reservation == null) {
            return false;
        }

        return reservation.getUser().getId().equals(userId);
    }

    /**
     * 사용자가 일요일 예약 관리 권한이 있는지 확인
     * - DORMITORY_COUNCIL 이상의 권한이 필요
     *
     * @param userId 사용자 ID
     * @return 권한이 있으면 true
     */
    @Transactional(readOnly = true)
    public boolean canManageSundayReservation(Long userId) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return false;
        }

        return user.getRole().canManageSundayReservation();
    }

    /**
     * 사용자가 DORMITORY_COUNCIL 권한이 있는지 확인
     *
     * @param userId 사용자 ID
     * @return DORMITORY_COUNCIL 권한이 있으면 true
     */
    @Transactional(readOnly = true)
    public boolean hasDormCouncilRole(Long userId) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return false;
        }

        return user.getRole() == UserRole.DORMITORY_COUNCIL || user.getRole() == UserRole.ADMIN;
    }

    /**
     * 사용자가 ADMIN 권한이 있는지 확인
     *
     * @param userId 사용자 ID
     * @return ADMIN 권한이 있으면 true
     */
    @Transactional(readOnly = true)
    public boolean hasAdminRole(Long userId) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return false;
        }

        return user.getRole() == UserRole.ADMIN;
    }
}
