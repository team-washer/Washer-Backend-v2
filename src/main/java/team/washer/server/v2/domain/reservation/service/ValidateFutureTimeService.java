package team.washer.server.v2.domain.reservation.service;

import java.time.LocalDateTime;

/**
 * 예약 시간이 미래인지 검증하는 서비스
 */
public interface ValidateFutureTimeService {

    /**
     * 예약 시간이 미래인지 검증
     *
     * @param startTime
     *            예약 시작 시간
     * @throws IllegalArgumentException
     *             과거 시간일 때
     */
    void execute(LocalDateTime startTime);
}
