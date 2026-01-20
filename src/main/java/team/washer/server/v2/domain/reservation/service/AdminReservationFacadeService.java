package team.washer.server.v2.domain.reservation.service;

import team.washer.server.v2.domain.reservation.dto.SundayStatusDto;

/**
 * 관리자 예약 관리 Facade 서비스.
 *
 * <p>관리자 권한 검증, 트랜잭션 관리, 도메인 서비스 조율을 담당합니다.
 */
public interface AdminReservationFacadeService {

    /**
     * 일요일 예약을 활성화합니다.
     *
     * @param adminId 관리자 ID
     * @param notes 활성화 메모
     * @throws IllegalArgumentException 관리자를 찾을 수 없거나 권한이 없는 경우
     */
    void activateSundayReservation(Long adminId, String notes);

    /**
     * 일요일 예약을 비활성화합니다.
     *
     * @param adminId 관리자 ID
     * @param notes 비활성화 메모
     * @throws IllegalArgumentException 관리자를 찾을 수 없거나 권한이 없는 경우
     */
    void deactivateSundayReservation(Long adminId, String notes);

    /**
     * 일요일 예약 상태를 조회합니다.
     *
     * @return 일요일 예약 상태 및 히스토리
     */
    SundayStatusDto getSundayReservationStatus();

    /**
     * 사용자의 패널티를 해제합니다.
     *
     * @param adminId 관리자 ID
     * @param userId 패널티를 해제할 사용자 ID
     * @throws IllegalArgumentException 관리자를 찾을 수 없거나 권한이 없는 경우
     */
    void clearUserPenalty(Long adminId, Long userId);
}
