package team.washer.server.v2.domain.reservation.service.impl;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import team.washer.server.v2.domain.machine.repository.MachineRepository;
import team.washer.server.v2.domain.notification.support.ReservationNotificationSupport;
import team.washer.server.v2.domain.reservation.entity.Reservation;
import team.washer.server.v2.domain.reservation.enums.ReservationStatus;
import team.washer.server.v2.domain.reservation.repository.ReservationRepository;
import team.washer.server.v2.domain.smartthings.dto.response.SmartThingsDeviceStatusResDto;
import team.washer.server.v2.domain.smartthings.support.MachineStateDetectionSupport;
import team.washer.server.v2.global.common.constants.ReservationConstants;
import team.washer.server.v2.global.util.DateTimeUtil;

/**
 * 예약 라이프사이클 처리의 트랜잭션 경계를 담당하는 컴포넌트.
 *
 * <p>
 * SmartThings 외부 API 호출은 호출 측({@code ProcessReservationLifecycleServiceImpl})이
 * 트랜잭션 밖에서 수행하고, 이 컴포넌트는 조회된 상태를 바탕으로 개별 예약의 DB 갱신만 짧은 독립 트랜잭션
 * ({@link Propagation#REQUIRES_NEW})으로 처리한다. 외부 API 호출이 DB 커넥션을 점유하지 않도록 하여 커넥션
 * 풀 고갈을 방지한다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ReservationLifecycleProcessor {

    private static final long COMPLETION_EARLY_LOG_TOLERANCE_MINUTES = 2;
    private static final long MAX_REASONABLE_CYCLE_MINUTES = 240;

    private final ReservationRepository reservationRepository;
    private final MachineRepository machineRepository;
    private final MachineStateDetectionSupport machineStateDetectionSupport;
    private final ReservationNotificationSupport reservationNotificationSupport;

    /**
     * 외부 API 호출 대상이 되는 예약의 식별자와 기기 ID 쌍.
     */
    public record LifecycleTarget(Long reservationId, String deviceId) {
    }

    /**
     * 지정한 상태의 예약 목록을 조회하여 처리 대상(예약 ID, 기기 ID)을 반환한다. 짧은 읽기 전용 트랜잭션으로 수행되며, 반환 후에는
     * 영속성 컨텍스트와 분리된 값만 남는다.
     */
    @Transactional(readOnly = true)
    public List<LifecycleTarget> findTargets(ReservationStatus status) {
        return reservationRepository.findByStatusWithMachineAndUser(status).stream()
                .map(reservation -> new LifecycleTarget(reservation.getId(), reservation.getMachine().getDeviceId()))
                .toList();
    }

    /**
     * RESERVED 예약을 기기 상태에 따라 RUNNING으로 전환한다. 외부 API 호출 이후의 DB 갱신만 독립 트랜잭션으로 처리한다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processReservedToRunning(Long reservationId, SmartThingsDeviceStatusResDto status) {
        var reservation = reservationRepository.findById(reservationId).orElse(null);
        if (reservation == null || !reservation.isReserved()) {
            return;
        }
        var machine = reservation.getMachine();
        if (!machineStateDetectionSupport.isRunning(status, machine.isWasher())) {
            return;
        }

        var expectedCompletionTime = DateTimeUtil.parseAndConvertToKoreaTime(status.getCompletionTime());
        reservation.start(expectedCompletionTime);
        machine.markAsInUse();
        reservationRepository.save(reservation);
        machineRepository.save(machine);

        reservationNotificationSupport.sendStarted(reservation.getUser(), machine, expectedCompletionTime);

        log.info("Reservation {} started (RESERVED → RUNNING)", reservation.getId());
    }

    /**
     * RUNNING 예약을 기기 상태에 따라 완료·중단·일시정지·진행으로 처리한다. 외부 API 호출 이후의 DB 갱신만 독립 트랜잭션으로
     * 처리한다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processRunningToCompleted(Long reservationId, SmartThingsDeviceStatusResDto status) {
        var reservation = reservationRepository.findById(reservationId).orElse(null);
        if (reservation == null || !reservation.isRunning()) {
            return;
        }
        var machine = reservation.getMachine();
        var isWasher = machine.isWasher();
        var completionTime = machineStateDetectionSupport.isCompleted(status, isWasher);

        if (completionTime.isPresent()) {
            if (isStaleCompletion(reservation, status, isWasher, completionTime.get())) {
                log.info(
                        "completion deferred reason=stale_completion reservationId={} startTime={} expectedCompletionTime={} completionTime={}",
                        reservation.getId(),
                        reservation.getStartTime(),
                        reservation.getExpectedCompletionTime(),
                        completionTime.get());
                return;
            }
            if (isTooEarlyCompletion(reservation, completionTime.get())) {
                if (!canAcceptEarlyCompletion(reservation, status, isWasher, completionTime.get())) {
                    log.info(
                            "completion deferred reason=too_early_completion reservationId={} startTime={} expectedCompletionTime={} completionTime={}",
                            reservation.getId(),
                            reservation.getStartTime(),
                            reservation.getExpectedCompletionTime(),
                            completionTime.get());
                    return;
                }
                logEarlyCompletionAccepted(reservation, completionTime.get());
            }
            reservation.complete();
            machine.markAsAvailable();
            reservationRepository.save(reservation);
            machineRepository.save(machine);

            reservationNotificationSupport.sendCompletion(reservation.getUser(), machine);

            log.info(
                    "Reservation completed reservationId={} userId={} machineId={} machineName={} completionTime={} expectedCompletionTime={}",
                    reservation.getId(),
                    reservation.getUser().getId(),
                    machine.getId(),
                    machine.getName(),
                    completionTime.get(),
                    reservation.getExpectedCompletionTime());
        } else if (machineStateDetectionSupport.isInterrupted(status, isWasher)) {
            // 사이클 단계 전환 중 순간적으로 보고되는 정지를 진짜 중단으로 오판하지 않도록, 연속으로 중단이
            // 감지될 때만 취소를 확정한다.
            reservation.incrementInterruptionCount();
            if (reservation.getInterruptionCount() >= ReservationConstants.INTERRUPTION_CONFIRM_THRESHOLD) {
                reservation.cancel();
                reservation.clearInterruptionCount();
                machine.markAsAvailable();
                reservationRepository.save(reservation);
                machineRepository.save(machine);

                reservationNotificationSupport.sendInterruption(reservation.getUser(), machine);

                log.warn(
                        "Reservation {} cancelled due to confirmed machine interruption, no penalty applied (RUNNING → CANCELLED)",
                        reservation.getId());
            } else {
                reservationRepository.save(reservation);
                log.warn("Reservation {} interruption suspected count={} threshold={} deferring cancellation",
                        reservation.getId(),
                        reservation.getInterruptionCount(),
                        ReservationConstants.INTERRUPTION_CONFIRM_THRESHOLD);
            }
        } else if (machineStateDetectionSupport.isPaused(status, isWasher)) {
            if (reservation.getInterruptionCount() > 0) {
                reservation.clearInterruptionCount();
                reservationRepository.save(reservation);
            }
            if (reservation.getPausedAt() == null) {
                reservation.markAsPaused();
                reservationRepository.save(reservation);
                log.info("Reservation {} pause started, tracking pause time", reservation.getId());
            } else if (Duration.between(reservation.getPausedAt(), DateTimeUtil.nowInKorea())
                    .toMinutes() >= ReservationConstants.PAUSE_TIMEOUT_MINUTES) {
                reservation.cancel();
                reservation.clearPausedAt();
                machine.markAsAvailable();
                reservationRepository.save(reservation);
                machineRepository.save(machine);

                reservationNotificationSupport.sendPauseTimeout(reservation.getUser(), machine);

                log.warn(
                        "Reservation {} cancelled due to prolonged pause ({}min+), no penalty applied (RUNNING → CANCELLED)",
                        reservation.getId(),
                        ReservationConstants.PAUSE_TIMEOUT_MINUTES);
            }
        } else {
            if (reservation.getInterruptionCount() > 0) {
                reservation.clearInterruptionCount();
                reservationRepository.save(reservation);
            }
            if (reservation.getPausedAt() != null) {
                reservation.clearPausedAt();
                log.info("Reservation {} resumed from pause, clearing pause tracking", reservation.getId());
            }
            var updatedExpectedCompletionTime = DateTimeUtil.parseAndConvertToKoreaTime(status.getCompletionTime());
            var current = reservation.getExpectedCompletionTime();
            if (updatedExpectedCompletionTime != null && (current == null
                    || Math.abs(Duration.between(current, updatedExpectedCompletionTime).toSeconds()) >= 60)) {
                reservation.updateExpectedCompletionTime(updatedExpectedCompletionTime);
                reservationRepository.save(reservation);
            }
        }
    }

    private boolean isStaleCompletion(Reservation reservation,
            SmartThingsDeviceStatusResDto status,
            boolean isWasher,
            LocalDateTime completionTime) {
        var startTime = reservation.getStartTime();
        if (startTime == null) {
            return false;
        }
        if (completionTime.isBefore(startTime)) {
            return true;
        }
        return isTimestampBeforeStart(getOperatingStateTimestamp(status, isWasher), startTime)
                || isTimestampBeforeStart(getJobStateTimestamp(status, isWasher), startTime);
    }

    private boolean isTooEarlyCompletion(Reservation reservation, LocalDateTime completionTime) {
        var expectedCompletionTime = reservation.getExpectedCompletionTime();
        if (expectedCompletionTime == null) {
            return false;
        }
        var earliestCompletionTime = expectedCompletionTime.minusMinutes(COMPLETION_EARLY_LOG_TOLERANCE_MINUTES);
        return completionTime.isBefore(earliestCompletionTime);
    }

    private boolean canAcceptEarlyCompletion(Reservation reservation,
            SmartThingsDeviceStatusResDto status,
            boolean isWasher,
            LocalDateTime completionTime) {
        return hasSuspiciousExpectedCompletionTime(reservation)
                && hasFreshCompletionEvidence(reservation, status, isWasher, completionTime);
    }

    private boolean hasSuspiciousExpectedCompletionTime(Reservation reservation) {
        var startTime = reservation.getStartTime();
        var expectedCompletionTime = reservation.getExpectedCompletionTime();
        if (startTime == null || expectedCompletionTime == null) {
            return false;
        }
        return Duration.between(startTime, expectedCompletionTime).toMinutes() > MAX_REASONABLE_CYCLE_MINUTES;
    }

    private boolean hasFreshCompletionEvidence(Reservation reservation,
            SmartThingsDeviceStatusResDto status,
            boolean isWasher,
            LocalDateTime completionTime) {
        var startTime = reservation.getStartTime();
        if (startTime == null) {
            return false;
        }
        var completionTimeStr = getCompletionTime(status, isWasher);
        if (completionTimeStr != null && !completionTimeStr.isBlank() && !completionTime.isBefore(startTime)) {
            return true;
        }
        return isTimestampAtOrAfterStart(getOperatingStateTimestamp(status, isWasher), startTime)
                || isTimestampAtOrAfterStart(getJobStateTimestamp(status, isWasher), startTime);
    }

    private void logEarlyCompletionAccepted(Reservation reservation, LocalDateTime completionTime) {
        log.info(
                "completion accepted reason=suspicious_expected_completion_time reservationId={} startTime={} expectedCompletionTime={} completionTime={}",
                reservation.getId(),
                reservation.getStartTime(),
                reservation.getExpectedCompletionTime(),
                completionTime);
    }

    private boolean isTimestampBeforeStart(String timestamp, LocalDateTime startTime) {
        if (timestamp == null || timestamp.isBlank()) {
            return false;
        }
        var updatedAt = DateTimeUtil.parseAndConvertToKoreaTime(timestamp);
        return updatedAt != null && updatedAt.isBefore(startTime);
    }

    private boolean isTimestampAtOrAfterStart(String timestamp, LocalDateTime startTime) {
        if (timestamp == null || timestamp.isBlank()) {
            return false;
        }
        var updatedAt = DateTimeUtil.parseAndConvertToKoreaTime(timestamp);
        return updatedAt != null && !updatedAt.isBefore(startTime);
    }

    private String getCompletionTime(SmartThingsDeviceStatusResDto status, boolean isWasher) {
        if (status == null) {
            return null;
        }
        return isWasher ? status.getWasherCompletionTime() : status.getDryerCompletionTime();
    }

    private String getOperatingStateTimestamp(SmartThingsDeviceStatusResDto status, boolean isWasher) {
        if (status == null) {
            return null;
        }
        return isWasher ? status.getWasherOperatingStateTimestamp() : status.getDryerOperatingStateTimestamp();
    }

    private String getJobStateTimestamp(SmartThingsDeviceStatusResDto status, boolean isWasher) {
        if (status == null) {
            return null;
        }
        return isWasher ? status.getWasherJobStateTimestamp() : status.getDryerJobStateTimestamp();
    }
}
