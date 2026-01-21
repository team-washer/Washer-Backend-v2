package team.washer.server.v2.domain.reservation.service.impl;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import team.washer.server.v2.domain.reservation.service.ValidateFutureTimeService;

/**
 * 예약 시간이 미래인지 검증하는 서비스 구현체
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ValidateFutureTimeServiceImpl implements ValidateFutureTimeService {

    @Override
    public void execute(final LocalDateTime startTime) {
        if (startTime.isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("예약 시간은 현재 시간 이후여야 합니다");
        }
        log.debug("Future time validation passed for {}", startTime);
    }
}
