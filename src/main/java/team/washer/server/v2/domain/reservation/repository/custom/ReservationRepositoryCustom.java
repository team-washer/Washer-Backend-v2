package team.washer.server.v2.domain.reservation.repository.custom;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import team.washer.server.v2.domain.machine.entity.Machine;
import team.washer.server.v2.domain.machine.enums.MachineType;
import team.washer.server.v2.domain.reservation.entity.Reservation;
import team.washer.server.v2.domain.reservation.enums.ReservationStatus;
import team.washer.server.v2.domain.user.entity.User;

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
     * @param machineType
     *            기기 유형 (세탁기/건조기, null 가능)
     * @param pageable
     *            페이지네이션 정보
     * @return 필터링된 예약 페이지 (생성일 기준 내림차순)
     */
    Page<Reservation> findAllWithFilters(String userName,
            String machineName,
            ReservationStatus status,
            LocalDateTime startDate,
            LocalDateTime endDate,
            MachineType machineType,
            Pageable pageable);

    /**
     * 호실 번호 기준 활성 예약 목록 조회
     *
     * @param roomNumber
     *            호실 번호
     * @return 해당 호실의 활성(RESERVED/RUNNING) 예약 목록 (createdAt 내림차순)
     */
    List<Reservation> findActiveReservationsByRoomNumber(String roomNumber);

    /**
     * 사용자의 현재 활성 예약 목록을 조회합니다. 타임아웃이 지난 RESERVED 예약은 쿼리 단계에서 제외됩니다.
     *
     * @param user
     *            조회 대상 사용자
     * @return 만료되지 않은 활성 예약 목록 (createdAt 내림차순)
     */
    List<Reservation> findCurrentlyActiveByUser(User user);

    /**
     * 기기의 현재 활성 예약 목록을 조회합니다. 타임아웃이 지난 RESERVED 예약은 쿼리 단계에서 제외됩니다.
     *
     * @param machine
     *            조회 대상 기기
     * @return 만료되지 않은 활성 예약 목록 (createdAt 내림차순)
     */
    List<Reservation> findCurrentlyActiveByMachine(Machine machine);

    /**
     * 호실의 현재 활성 예약 목록을 조회합니다. 타임아웃이 지난 RESERVED 예약은 쿼리 단계에서 제외됩니다.
     *
     * @param roomNumber
     *            호실 번호
     * @return 만료되지 않은 활성 예약 목록 (createdAt 내림차순)
     */
    List<Reservation> findCurrentlyActiveByRoomNumber(String roomNumber);

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

    /**
     * 관리자용 전체 기기 예약 히스토리 조회 (기기명 부분 검색 지원)
     *
     * @param machineName
     *            기기명 (부분 검색, null 또는 빈 문자열이면 전체 조회)
     * @return 기기명 오름차순, 생성일 내림차순으로 정렬된 예약 목록
     */
    List<Reservation> findAllByMachineNameFilter(String machineName);
}
