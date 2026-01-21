package team.washer.server.v2.domain.reservation.service.impl;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import team.washer.server.v2.domain.reservation.entity.ReservationCycleLog;
import team.washer.server.v2.domain.reservation.enums.CycleAction;
import team.washer.server.v2.domain.reservation.repository.ReservationCycleLogRepository;
import team.washer.server.v2.domain.reservation.service.PersistSundayReservationActivationService;
import team.washer.server.v2.domain.user.entity.User;

/**
 * 일요일 예약 활성화/비활성화 영속화 서비스 구현체
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PersistSundayReservationActivationServiceImpl implements PersistSundayReservationActivationService {

    private static final String SUNDAY_ACTIVE_KEY = "reservation:sunday:active";

    private final RedisTemplate<String, String> redisTemplate;
    private final ReservationCycleLogRepository cycleLogRepository;

    @Override
    @Transactional
    public void execute(final User performedBy, final String notes, final boolean isActive) {
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
