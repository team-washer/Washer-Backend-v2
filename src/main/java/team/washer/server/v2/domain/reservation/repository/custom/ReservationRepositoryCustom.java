package team.washer.server.v2.domain.reservation.repository.custom;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import team.washer.server.v2.domain.machine.enums.MachineType;
import team.washer.server.v2.domain.reservation.entity.Reservation;
import team.washer.server.v2.domain.reservation.enums.ReservationStatus;

public interface ReservationRepositoryCustom {

    Page<Reservation> findReservationHistory(Long userId,
            ReservationStatus status,
            LocalDateTime startDate,
            LocalDateTime endDate,
            MachineType machineType,
            Pageable pageable);

    boolean existsConflictingReservation(Long machineId,
            LocalDateTime startTime,
            LocalDateTime endTime,
            Long excludeReservationId);

    /**
     * 동일 호실의 동일 유형 기기 활성 예약 존재 여부 확인
     *
     * @param roomNumber
     *            호실 번호
     * @param machineType
     *            기기 유형 (세탁기/건조기)
     * @return 동일 유형 활성 예약 존재 여부
     */
    boolean existsActiveReservationByRoomAndMachineType(String roomNumber, MachineType machineType);

    List<Reservation> findExpiredReservations(ReservationStatus status,
            LocalDateTime threshold,
            LocalDateTime recentCutoff);

    /**
     * 관리자용 예약 목록 조회 (동적 필터링)
     *
     * @param userName
     *            사용자 이름 (부분 검색, null 가능)
     * @param machineName
     *            기기명 (부분 검색, null 가능)
     * @param status
     *            예약 상태 (null 가능)
     * @param startDate
     *            시작일 (null 가능)
     * @param endDate
     *            종료일 (null 가능)
     * @param pageable
     *            페이지네이션 정보
     * @return 필터링된 예약 페이지 (생성일 기준 내림차순)
     */
    Page<Reservation> findAllWithFilters(String userName,
            String machineName,
            ReservationStatus status,
            LocalDateTime startDate,
            LocalDateTime endDate,
            Pageable pageable);

    /**
     * 호실 번호 기준 활성 예약 목록 조회
     *
     * @param roomNumber
     *            호실 번호
     * @return 해당 호실의 활성(RESERVED/CONFIRMED/RUNNING) 예약 목록 (createdAt 내림차순)
     */
    List<Reservation> findActiveReservationsByRoomNumber(String roomNumber);

    /**
     * 기기별 예약 히스토리 조회
     *
     * @param machineId
     *            기기 ID (필수)
     * @param status
     *            예약 상태 (null 가능)
     * @param startDate
     *            시작일 (null 가능)
     * @param endDate
     *            종료일 (null 가능)
     * @param pageable
     *            페이지네이션 정보
     * @return 기기별 예약 히스토리 페이지 (생성일 기준 내림차순)
     */
    Page<Reservation> findMachineReservationHistory(Long machineId,
            ReservationStatus status,
            LocalDateTime startDate,
            LocalDateTime endDate,
            Pageable pageable);
}
