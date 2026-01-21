package team.washer.server.v2.domain.reservation.service;

import java.time.LocalDateTime;

import team.washer.server.v2.domain.user.entity.User;

/**
 * 예약 시간 제한 검증 서비스 (월~목 21:10 이후, 금토 제한 없음, 일요일은 활성화 필요)
 */
public interface ValidateTimeRestrictionService {

    /**
     * 예약 시간 제한 검증
     *
     * @param user
     *            예약하려는 사용자
     * @param startTime
     *            예약 시작 시간
     * @throws IllegalArgumentException
     *             시간 제한 위반 시
     */
    void execute(User user, LocalDateTime startTime);
}
