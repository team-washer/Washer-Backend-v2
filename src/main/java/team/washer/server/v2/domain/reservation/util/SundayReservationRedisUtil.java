package team.washer.server.v2.domain.reservation.util;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import team.washer.server.v2.domain.reservation.entity.ReservationCycleLog;
import team.washer.server.v2.domain.reservation.enums.CycleAction;
import team.washer.server.v2.domain.reservation.repository.ReservationCycleLogRepository;
import team.washer.server.v2.domain.user.entity.User;

@Slf4j
@Component
@RequiredArgsConstructor
public class SundayReservationRedisUtil {

    private static final String SUNDAY_ACTIVE_KEY = "reservation:sunday:active";

    private final RedisTemplate<String, String> redisTemplate;
    private final ReservationCycleLogRepository cycleLogRepository;

    public boolean isSundayActive() {
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

    public void persistActivation(final User performedBy, final String notes, final boolean isActive) {
        if (isActive) {
            activateSundayReservation(performedBy, notes);
        } else {
            deactivateSundayReservation(performedBy, notes);
        }
    }

    private void activateSundayReservation(final User performedBy, final String notes) {
        try {
            redisTemplate.opsForValue().set(SUNDAY_ACTIVE_KEY, "true");
            log.info("Activated Sunday reservation by user {}", performedBy.getId());
        } catch (Exception e) {
            log.error("Failed to activate Sunday reservation in Redis", e);
            throw new RuntimeException("일요일 예약 활성화에 실패했습니다", e);
        }
        final ReservationCycleLog cycleLog = ReservationCycleLog.builder().isActive(true).action(CycleAction.ACTIVATED)
                .performedBy(performedBy).notes(notes).build();

        cycleLogRepository.save(cycleLog);
    }

    private void deactivateSundayReservation(final User performedBy, final String notes) {
        try {
            redisTemplate.delete(SUNDAY_ACTIVE_KEY);
            log.info("Deactivated Sunday reservation by user {}", performedBy.getId());
        } catch (Exception e) {
            log.error("Failed to deactivate Sunday reservation in Redis", e);
            throw new RuntimeException("일요일 예약 비활성화에 실패했습니다", e);
        }

        final ReservationCycleLog cycleLog = ReservationCycleLog.builder().isActive(false)
                .action(CycleAction.DEACTIVATED).performedBy(performedBy).notes(notes).build();

        cycleLogRepository.save(cycleLog);
    }
}
