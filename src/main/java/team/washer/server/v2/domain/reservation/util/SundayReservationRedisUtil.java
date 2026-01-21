package team.washer.server.v2.domain.reservation.util;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import team.washer.server.v2.domain.reservation.entity.ReservationCycleLog;
import team.washer.server.v2.domain.reservation.enums.CycleAction;
import team.washer.server.v2.domain.reservation.repository.ReservationCycleLogRepository;
import team.washer.server.v2.domain.user.entity.User;

/**
 * 일요일 예약 활성화 관련 Redis 작업을 처리하는 유틸리티 컴포넌트
 *
 * <p>
 * Redis를 우선적으로 사용하며, 장애 시 데이터베이스로 폴백한다. 모든 활성화 작업은 Redis와 DB 양쪽에 동기화된다.
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SundayReservationRedisUtil {

    private static final String SUNDAY_ACTIVE_KEY = "reservation:sunday:active";

    private final RedisTemplate<String, String> redisTemplate;
    private final ReservationCycleLogRepository cycleLogRepository;

    /**
     * 일요일 예약 활성화 여부 조회
     *
     * <p>
     * Redis를 우선적으로 확인하고, 실패 시 데이터베이스의 최신 로그로 폴백한다.
     * </p>
     *
     * @return 일요일 예약 활성화 여부
     */
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

    /**
     * 일요일 예약 활성화/비활성화 영속화
     *
     * <p>
     * Redis와 데이터베이스에 활성화 상태를 저장한다. 활성화 시 Redis에 "true" 값을 설정하고, 비활성화 시 키를 삭제한다.
     * 데이터베이스에는 ReservationCycleLog를 생성하여 이력을 기록한다.
     * </p>
     *
     * @param performedBy
     *            작업을 수행한 사용자
     * @param notes
     *            작업 메모
     * @param isActive
     *            활성화 여부
     */
    public void persistActivation(final User performedBy, final String notes, final boolean isActive) {
        if (isActive) {
            activateSundayReservation(performedBy, notes);
        } else {
            deactivateSundayReservation(performedBy, notes);
        }
    }

    /**
     * 일요일 예약 활성화 (내부 메서드)
     *
     * @param performedBy
     *            활성화를 수행한 사용자
     * @param notes
     *            활성화 메모
     */
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

    /**
     * 일요일 예약 비활성화 (내부 메서드)
     *
     * @param performedBy
     *            비활성화를 수행한 사용자
     * @param notes
     *            비활성화 메모
     */
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
