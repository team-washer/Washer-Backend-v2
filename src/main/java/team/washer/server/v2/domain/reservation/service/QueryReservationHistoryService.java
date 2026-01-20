package team.washer.server.v2.domain.reservation.service;

import java.time.LocalDateTime;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import team.washer.server.v2.domain.machine.enums.MachineType;
import team.washer.server.v2.domain.reservation.dto.response.ReservationHistoryResDto;
import team.washer.server.v2.domain.reservation.enums.ReservationStatus;

public interface GetReservationHistoryService {

    /**
     * 예약 히스토리를 조회합니다.
     *
     * @param userId
     *            사용자 ID
     * @param status
     *            예약 상태 필터
     * @param startDate
     *            시작 날짜 필터
     * @param endDate
     *            종료 날짜 필터
     * @param machineType
     *            기기 타입 필터
     * @param pageable
     *            페이지네이션
     * @return 예약 히스토리 페이지
     */
    Page<ReservationHistoryResDto> getReservationHistory(Long userId,
            ReservationStatus status,
            LocalDateTime startDate,
            LocalDateTime endDate,
            MachineType machineType,
            Pageable pageable);
}
