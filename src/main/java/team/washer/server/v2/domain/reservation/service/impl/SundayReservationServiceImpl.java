package team.washer.server.v2.domain.reservation.service.impl;

import java.util.List;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import team.washer.server.v2.domain.reservation.entity.ReservationCycleLog;
import team.washer.server.v2.domain.reservation.enums.CycleAction;
import team.washer.server.v2.domain.reservation.repository.ReservationCycleLogRepository;
import team.washer.server.v2.domain.reservation.service.SundayReservationService;
import team.washer.server.v2.domain.user.entity.User;

@Slf4j
@Service
@RequiredArgsConstructor
public class SundayReservationServiceImpl implements SundayReservationService {

    private static final String SUNDAY_ACTIVE_KEY = "reservation:sunday:active";

    private final RedisTemplate<String, String> redisTemplate;
    private final ReservationCycleLogRepository cycleLogRepository;

    @Override
    @Transactional
    public void activateSundayReservation(User performedBy, String notes) {
        try {
            // Redis에 활성화 상태 저장 (만료 시간 없음)
            redisTemplate.opsForValue().set(SUNDAY_ACTIVE_KEY, "true");
            log.info("Activated Sunday reservation by user {}", performedBy.getId());
        } catch (Exception e) {
            log.error("Failed to activate Sunday reservation in Redis", e);
            throw new RuntimeException("일요일 예약 활성화에 실패했습니다", e);
        }

        // DB에 감사 로그 저장
        ReservationCycleLog log = ReservationCycleLog.builder().isActive(true).action(CycleAction.ACTIVATED)
                .performedBy(performedBy).notes(notes).build();

        cycleLogRepository.save(log);
    }

    @Override
    @Transactional
    public void deactivateSundayReservation(User performedBy, String notes) {
        try {
            // Redis에서 활성화 상태 제거
            redisTemplate.delete(SUNDAY_ACTIVE_KEY);
            log.info("Deactivated Sunday reservation by user {}", performedBy.getId());
        } catch (Exception e) {
            log.error("Failed to deactivate Sunday reservation in Redis", e);
            throw new RuntimeException("일요일 예약 비활성화에 실패했습니다", e);
        }

        // DB에 감사 로그 저장
        ReservationCycleLog log = ReservationCycleLog.builder().isActive(false).action(CycleAction.DEACTIVATED)
                .performedBy(performedBy).notes(notes).build();

        cycleLogRepository.save(log);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isSundayReservationActive() {
        try {
            // Redis에서 활성화 상태 확인
            String value = redisTemplate.opsForValue().get(SUNDAY_ACTIVE_KEY);
            boolean isActive = "true".equals(value);
            log.debug("Sunday reservation active status: {}", isActive);
            return isActive;
        } catch (Exception e) {
            log.error("Failed to check Sunday reservation status in Redis, falling back to database", e);

            // Redis 실패 시 DB 폴백
            ReservationCycleLog latestLog = cycleLogRepository.findLatest();
            if (latestLog == null) {
                return false;
            }
            return latestLog.getIsActive();
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReservationCycleLog> getSundayReservationHistory() {
        return cycleLogRepository.findAllOrderByCreatedAtDesc();
    }
}
