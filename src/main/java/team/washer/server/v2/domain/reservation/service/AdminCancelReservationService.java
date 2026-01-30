package team.washer.server.v2.domain.reservation.service;

import team.washer.server.v2.domain.reservation.dto.response.AdminCancellationResDto;

/**
 * 관리자 예약 강제 취소 서비스
 */
public interface AdminCancelReservationService {

    /**
     * 관리자가 예약을 강제로 취소 (패널티 없음)
     *
     * @param reservationId
     *            예약 ID
     * @return 취소된 예약 정보
     */
    AdminCancellationResDto execute(Long reservationId);
}
