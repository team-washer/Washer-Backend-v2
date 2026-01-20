package team.washer.server.v2.domain.reservation.service;

import java.time.LocalDateTime;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import team.washer.server.v2.domain.machine.enums.MachineType;
import team.washer.server.v2.domain.reservation.dto.*;
import team.washer.server.v2.domain.reservation.enums.ReservationStatus;

public interface ReservationService {

    /**
     * 예약 생성
     *
     * @param userId 사용자 ID
     * @param requestDto 예약 생성 요청 DTO
     * @return 생성된 예약 응답 DTO
     */
    ReservationResponseDto createReservation(Long userId, CreateReservationRequestDto requestDto);

    /**
     * 예약 확인
     *
     * @param userId 사용자 ID
     * @param reservationId 예약 ID
     * @return 확인된 예약 응답 DTO
     */
    ReservationResponseDto confirmReservation(Long userId, Long reservationId);

    /**
     * 기기 시작
     *
     * @param userId 사용자 ID
     * @param reservationId 예약 ID
     * @param requestDto 시작 요청 DTO
     * @return 시작된 예약 응답 DTO
     */
    ReservationResponseDto startReservation(Long userId, Long reservationId, StartReservationRequestDto requestDto);

    /**
     * 예약 취소
     *
     * @param userId 사용자 ID
     * @param reservationId 예약 ID
     * @return 취소 응답 DTO
     */
    CancellationResponseDto cancelReservation(Long userId, Long reservationId);

    /**
     * 활성 예약 조회
     *
     * @param userId 사용자 ID
     * @return 활성 예약 응답 DTO (없으면 null)
     */
    ReservationResponseDto getActiveReservation(Long userId);

    /**
     * 예약 히스토리 조회
     *
     * @param userId 사용자 ID
     * @param status 예약 상태 필터
     * @param startDate 시작 날짜 필터
     * @param endDate 종료 날짜 필터
     * @param machineType 기기 타입 필터
     * @param pageable 페이지네이션
     * @return 예약 히스토리 페이지
     */
    Page<ReservationHistoryDto> getReservationHistory(Long userId, ReservationStatus status, LocalDateTime startDate,
            LocalDateTime endDate, MachineType machineType, Pageable pageable);

    /**
     * 예약 조회
     *
     * @param reservationId 예약 ID
     * @return 예약 응답 DTO
     */
    ReservationResponseDto getReservation(Long reservationId);
}
