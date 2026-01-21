package team.washer.server.v2.domain.reservation.service.impl;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import team.washer.server.v2.domain.reservation.entity.ReservationCycleLog;
import team.washer.server.v2.domain.reservation.repository.ReservationCycleLogRepository;
import team.washer.server.v2.domain.reservation.service.QuerySundayReservationActiveService;

/**
 * 일요일 예약 활성화 상태 조회 서비스 구현체
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QuerySundayReservationActiveServiceImpl implements QuerySundayReservationActiveService {

    private static final String SUNDAY_ACTIVE_KEY = "reservation:sunday:active";

    private final RedisTemplate<String, String> redisTemplate;
    private final ReservationCycleLogRepository cycleLogRepository;

    @Override
    @Transactional(readOnly = true)
    public boolean execute() {
        try {
            final String value = redisTemplate.opsForValue().get(SUNDAY_ACTIVE_KEY);
            final boolean isActive = "true".equals(value);
            log.debug("Sunday reservation active status: {}", isActive);
            return isActive;
        } catch (Exception e) {
            log.error("Failed to check Sunday reservation status in Redis, falling back to database", e);
            final ReservationCycleLog latestLog = cycleLogRepository.findLatest();
            if (latestLog == null) {
                return false;
            }
            return latestLog.getIsActive();
        }
    }
}
