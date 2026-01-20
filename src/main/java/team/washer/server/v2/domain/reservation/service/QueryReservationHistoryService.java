package team.washer.server.v2.domain.reservation.service;

import java.time.LocalDateTime;

import org.springframework.data.domain.Pageable;

import team.washer.server.v2.domain.machine.enums.MachineType;
import team.washer.server.v2.domain.reservation.dto.response.ReservationHistoryPageResDto;
import team.washer.server.v2.domain.reservation.enums.ReservationStatus;

public interface QueryReservationHistoryService {

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
     *            페이징 정보
     * @return 예약 히스토리 페이지 응답 DTO
     */
    ReservationHistoryPageResDto execute(Long userId,
            ReservationStatus status,
            LocalDateTime startDate,
            LocalDateTime endDate,
            MachineType machineType,
            Pageable pageable);
}
