package team.washer.server.v2.domain.reservation.service;

import java.time.LocalDateTime;

import team.washer.server.v2.domain.machine.entity.Machine;

/**
 * 기기 가용성 검증 서비스 (예약 시간 충돌 확인)
 */
public interface ValidateMachineAvailabilityService {

    /**
     * 기기 가용성 검증
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
    void execute(Machine machine, LocalDateTime startTime, LocalDateTime endTime, Long excludeReservationId);
}
