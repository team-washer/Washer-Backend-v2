package team.washer.server.v2.domain.reservation.service.impl;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import team.washer.server.v2.domain.machine.entity.Machine;
import team.washer.server.v2.domain.machine.repository.MachineRepository;
import team.washer.server.v2.domain.notification.support.ReservationNotificationSupport;
import team.washer.server.v2.domain.reservation.entity.Reservation;
import team.washer.server.v2.domain.reservation.enums.ReservationStatus;
import team.washer.server.v2.domain.reservation.repository.ReservationRepository;
import team.washer.server.v2.domain.reservation.service.CancelOverdueReservationService;
import team.washer.server.v2.domain.reservation.util.PenaltyRedisUtil;
import team.washer.server.v2.domain.smartthings.support.DeviceStatusQuerySupport;
import team.washer.server.v2.domain.smartthings.support.MachineStateDetectionSupport;
import team.washer.server.v2.domain.user.entity.User;
import team.washer.server.v2.global.common.constants.PenaltyConstants;
import team.washer.server.v2.global.util.DateTimeUtil;

@Slf4j
@Service
@RequiredArgsConstructor
public class CancelOverdueReservationServiceImpl implements CancelOverdueReservationService {

    private final ReservationRepository reservationRepository;
    private final MachineRepository machineRepository;
    private final PenaltyRedisUtil penaltyRedisUtil;
    private final ReservationNotificationSupport reservationNotificationSupport;
    private final MachineStateDetectionSupport machineStateDetectionSupport;
    private final DeviceStatusQuerySupport deviceStatusQuerySupport;

    @Override
    @Transactional
    public void execute() {
        // reservedAt 기준 3분 초과 (타임아웃 상수 사용)
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(ReservationStatus.RESERVED.getTimeoutMinutes());
        LocalDateTime recentCutoff = LocalDateTime.now().minusHours(24);

        List<Reservation> expiredReservations = reservationRepository
                .findExpiredReservations(ReservationStatus.RESERVED, threshold, recentCutoff);

        if (expiredReservations.isEmpty()) {
            return;
        }

        var autoStarted = new ArrayList<Long>();
        var cancelled = new ArrayList<Long>();

        for (Reservation reservation : expiredReservations) {
            try {
                var machine = reservation.getMachine();
                var isRunning = machineStateDetectionSupport.isRunning(machine.getDeviceId());

                if (isRunning) {
                    var expectedCompletionTime = DateTimeUtil.getExpectedCompletionTime(deviceStatusQuerySupport,
                            machine.getDeviceId());
                    reservation.start(expectedCompletionTime);
                    machine.markAsInUse();
                    reservationRepository.save(reservation);
                    machineRepository.save(machine);
                    reservationNotificationSupport.sendStarted(reservation.getUser(), machine, expectedCompletionTime);
                    autoStarted.add(reservation.getId());
                } else {
                    reservation.cancel();
                    machine.markAsAvailable();
                    reservationRepository.save(reservation);
                    machineRepository.save(machine);

                    User user = reservation.getUser();
                    applyTimeoutPenalty(user, machine);
                    cancelled.add(reservation.getId());
                }
            } catch (Exception e) {
                log.error("reservation timeout error processing RESERVED reservation={}", reservation.getId(), e);
            }
        }

        log.info("reservation timeout RESERVED processed={} auto_started={} {} cancelled={} {}",
                expiredReservations.size(),
                autoStarted.size(),
                autoStarted,
                cancelled.size(),
                cancelled);
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

        penaltyRedisUtil.applyCooldown(userId);
        penaltyRedisUtil.recordCancellation(userId);

        if (!penaltyRedisUtil.hasWarning(userId)) {
            penaltyRedisUtil.applyWarning(userId);
            reservationNotificationSupport.sendTimeoutWarning(user, machine);
            log.info("timeout first warning applied userId={}", userId);
        } else {
            reservationNotificationSupport.sendAutoCancellation(user, machine);
            log.info("timeout penalty applied userId={}", userId);
        }

        if (penaltyRedisUtil.getCancellationCount(userId) > PenaltyConstants.MAX_CANCELLATIONS_IN_48H) {
            penaltyRedisUtil.applyBlock(user.getRoomNumber());
            log.warn("48h block applied roomNumber={} exceeded max cancellations {}",
                    user.getRoomNumber(),
                    PenaltyConstants.MAX_CANCELLATIONS_IN_48H);
        }
    }
}
