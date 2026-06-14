package team.washer.server.v2.domain.reservation.service.impl;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import team.washer.server.v2.domain.machine.entity.Machine;
import team.washer.server.v2.domain.machine.repository.MachineRepository;
import team.washer.server.v2.domain.notification.support.ReservationNotificationSupport;
import team.washer.server.v2.domain.reservation.enums.ReservationStatus;
import team.washer.server.v2.domain.reservation.repository.ReservationRepository;
import team.washer.server.v2.domain.reservation.util.PenaltyRedisUtil;
import team.washer.server.v2.domain.smartthings.dto.response.SmartThingsDeviceStatusResDto;
import team.washer.server.v2.domain.smartthings.support.MachineStateDetectionSupport;
import team.washer.server.v2.domain.user.entity.User;
import team.washer.server.v2.global.common.constants.PenaltyConstants;
import team.washer.server.v2.global.util.DateTimeUtil;

/**
 * 만료된 예약 처리의 트랜잭션 경계를 담당하는 컴포넌트.
 *
 * <p>
 * SmartThings 외부 API 호출은 호출 측({@code CancelOverdueReservationServiceImpl})이
 * 트랜잭션 밖에서 수행하고, 이 컴포넌트는 조회된 상태를 바탕으로 개별 예약의 DB 갱신만 짧은 독립 트랜잭션
 * ({@link Propagation#REQUIRES_NEW})으로 처리한다. 외부 API 호출이 DB 커넥션을 점유하지 않도록 하여 커넥션
 * 풀 고갈을 방지한다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OverdueReservationProcessor {

    private final ReservationRepository reservationRepository;
    private final MachineRepository machineRepository;
    private final PenaltyRedisUtil penaltyRedisUtil;
    private final ReservationNotificationSupport reservationNotificationSupport;
    private final MachineStateDetectionSupport machineStateDetectionSupport;

    /**
     * 외부 API 호출 대상이 되는 만료 예약의 식별자와 기기 ID 쌍.
     */
    public record OverdueTarget(Long reservationId, String deviceId) {
    }

    /**
     * 개별 만료 예약 처리 결과. 호출 측의 요약 로그 집계에 사용한다.
     */
    public enum OverdueResult {
        AUTO_STARTED, CANCELLED, SKIPPED
    }

    /**
     * 타임아웃된 RESERVED 예약을 조회하여 처리 대상(예약 ID, 기기 ID)을 반환한다. 짧은 읽기 전용 트랜잭션으로 수행된다.
     */
    @Transactional(readOnly = true)
    public List<OverdueTarget> findExpiredTargets() {
        // reservedAt 기준 타임아웃 상수 초과
        var threshold = LocalDateTime.now().minusMinutes(ReservationStatus.RESERVED.getTimeoutMinutes());
        var recentCutoff = LocalDateTime.now().minusHours(24);

        return reservationRepository.findExpiredReservations(ReservationStatus.RESERVED, threshold, recentCutoff)
                .stream()
                .map(reservation -> new OverdueTarget(reservation.getId(), reservation.getMachine().getDeviceId()))
                .toList();
    }

    /**
     * 만료된 예약을 기기 상태에 따라 자동 시작하거나 취소(패널티 부여)한다. 외부 API 호출 이후의 DB 갱신만 독립 트랜잭션으로 처리한다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public OverdueResult processOverdue(Long reservationId, SmartThingsDeviceStatusResDto status) {
        var reservation = reservationRepository.findById(reservationId).orElse(null);
        if (reservation == null || !reservation.isReserved()) {
            return OverdueResult.SKIPPED;
        }
        var machine = reservation.getMachine();

        if (machineStateDetectionSupport.isRunning(status, machine.isWasher())) {
            var expectedCompletionTime = DateTimeUtil.parseAndConvertToKoreaTime(status.getCompletionTime());
            reservation.start(expectedCompletionTime);
            machine.markAsInUse();
            reservationRepository.save(reservation);
            machineRepository.save(machine);
            reservationNotificationSupport.sendStarted(reservation.getUser(), machine, expectedCompletionTime);
            return OverdueResult.AUTO_STARTED;
        }

        reservation.cancel();
        machine.markAsAvailable();
        reservationRepository.save(reservation);
        machineRepository.save(machine);

        applyTimeoutPenalty(reservation.getUser(), machine);
        return OverdueResult.CANCELLED;
    }

    /**
     * 타임아웃 취소 시 패널티를 적용합니다.
     * <p>
     * 1. 항상 5분 쿨다운 적용<br>
     * 2. 취소 횟수 기록 (48h 슬라이딩 윈도우)<br>
     * 3. 첫 번째 경고 여부에 따라 알림 분기<br>
     * 4. 48시간 내 {maxCount}회 초과 시 48h 블록 적용
     * </p>
     */
    private void applyTimeoutPenalty(User user, Machine machine) {
        final long userId = user.getId();

        penaltyRedisUtil.applyCooldown(userId, machine.getType());
        penaltyRedisUtil.recordCancellation(userId);
        user.updateLastCancellationTime();

        if (!penaltyRedisUtil.hasWarning(userId)) {
            penaltyRedisUtil.applyWarning(userId);
            reservationNotificationSupport.sendTimeoutWarning(user, machine);
            log.info("timeout first warning applied userId={}", userId);
        } else {
            reservationNotificationSupport.sendAutoCancellation(user, machine);
            log.info("timeout penalty applied userId={}", userId);
        }

        if (penaltyRedisUtil.getCancellationCount(userId) > PenaltyConstants.MAX_CANCELLATIONS_IN_48H) {
            final boolean wasBlocked = penaltyRedisUtil.isBlocked(user.getRoomNumber());
            penaltyRedisUtil.applyBlock(user.getRoomNumber());
            if (!wasBlocked) {
                reservationNotificationSupport.sendCancellationBlock(user, machine);
            }
            log.warn("48h block applied roomNumber={} exceeded max cancellations {}",
                    user.getRoomNumber(),
                    PenaltyConstants.MAX_CANCELLATIONS_IN_48H);
        }
    }
}
