package team.washer.server.v2.domain.reservation.service;

import java.time.LocalDateTime;

import team.washer.server.v2.domain.machine.entity.Machine;
import team.washer.server.v2.domain.user.entity.User;

public interface ReservationValidationService {

    /**
     * 예약 시간 제한 검증 (월~목 21:10 이후, 금토 제한 없음, 일요일은 활성화 필요)
     *
     * @param user
     *            예약하려는 사용자
     * @param startTime
     *            예약 시작 시간
     * @throws IllegalArgumentException
     *             시간 제한 위반 시
     */
    void validateTimeRestriction(User user, LocalDateTime startTime);

    /**
     * 사용자 패널티 상태 검증
     *
     * @param userId
     *            사용자 ID
     * @throws IllegalStateException
     *             패널티 상태일 때
     */
    void validateUserNotPenalized(Long userId);

    /**
     * 기기 가용성 검증 (예약 시간 충돌 확인)
     *
     * @param machine
     *            기기
     * @param startTime
     *            예약 시작 시간
     * @param endTime
     *            예약 종료 시간
     * @param excludeReservationId
     *            제외할 예약 ID (수정 시 사용)
     * @throws IllegalStateException
     *             기기가 사용 중일 때
     */
    void validateMachineAvailable(Machine machine,
            LocalDateTime startTime,
            LocalDateTime endTime,
            Long excludeReservationId);

    /**
     * 예약 시간이 미래인지 검증
     *
     * @param startTime
     *            예약 시작 시간
     * @throws IllegalArgumentException
     *             과거 시간일 때
     */
    void validateFutureTime(LocalDateTime startTime);
}
