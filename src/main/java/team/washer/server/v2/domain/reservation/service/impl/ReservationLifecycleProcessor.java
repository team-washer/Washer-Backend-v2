package team.washer.server.v2.domain.reservation.service.impl;

import java.time.Duration;
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
import team.washer.server.v2.domain.reservation.entity.Reservation;
import team.washer.server.v2.domain.reservation.enums.ReservationStatus;
import team.washer.server.v2.domain.reservation.repository.ReservationRepository;
import team.washer.server.v2.domain.reservation.support.CompletionDecision;
import team.washer.server.v2.domain.reservation.support.ReservationCompletionDecisionSupport;
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
 *
 * <p>
 * 완료 여부 판정 자체는 {@link ReservationCompletionDecisionSupport}가 전담하고, 이 컴포넌트는 그
 * 결과에 디바운스를 적용하여 상태 전이를 확정한다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ReservationLifecycleProcessor {

    private final ReservationRepository reservationRepository;
    private final MachineRepository machineRepository;
    private final MachineStateDetectionSupport machineStateDetectionSupport;
    private final ReservationCompletionDecisionSupport completionDecisionSupport;
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

        var reportedCompletionTime = DateTimeUtil
                .parseAndConvertToKoreaTime(status.getCompletionTime(machine.isWasher()));
        reservation.start(reportedCompletionTime);
        machine.markAsInUse();
        reservationRepository.save(reservation);
        machineRepository.save(machine);

        var expectedCompletionTime = reservation.getExpectedCompletionTime();
        if (reportedCompletionTime != null && expectedCompletionTime == null) {
            log.warn(
                    "expected completion time rejected on start reservationId={} startTime={} reported={} maxCycleMinutes={}",
                    reservation.getId(),
                    reservation.getStartTime(),
                    reportedCompletionTime,
                    ReservationConstants.MAX_REASONABLE_CYCLE_MINUTES);
        }

        reservationNotificationSupport.sendStarted(reservation.getUser(), machine, expectedCompletionTime);

        log.info("Reservation {} started (RESERVED → RUNNING)", reservation.getId());
    }

    /**
     * RUNNING 예약을 기기 상태에 따라 완료·중단·일시정지·진행으로 처리한다. 외부 API 호출 이후의 DB 갱신만 독립 트랜잭션으로
     * 처리한다.
     *
     * <p>
     * 판정 순서는 완료 → 전원 차단 → 완료 예정 시각 근처 정지 → 비정상 중단 → 일시정지 → 진행이다. 전원 차단은 사이클 종료 직전의
     * 정지 보류보다 앞에 두어, 유예 범위 안에서 중단이 영영 확정되지 않는 상황을 막는다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processRunningToCompleted(Long reservationId, SmartThingsDeviceStatusResDto status) {
        var reservation = reservationRepository.findById(reservationId).orElse(null);
        if (reservation == null || !reservation.isRunning()) {
            return;
        }
        var machine = reservation.getMachine();
        var isWasher = machine.isWasher();

        var decision = completionDecisionSupport.decide(reservation, status, isWasher);
        if (decision.isCompleted()) {
            processCompletionCandidate(reservation, machine, decision);
            return;
        }

        clearCompletionCount(reservation);
        if (decision.isDeferred()) {
            logCompletionDeferred(reservation, decision.reason(), decision.completionTime());
            return;
        }

        if (machineStateDetectionSupport.isPoweredOff(status)) {
            processInterruption(reservation, machine);
            return;
        }
        if (completionDecisionSupport.isStoppedNearCompletion(reservation, status, isWasher)) {
            logCompletionDeferred(reservation, "stopped_near_completion", reservation.getExpectedCompletionTime());
            return;
        }
        if (machineStateDetectionSupport.isInterrupted(status, isWasher)) {
            processInterruption(reservation, machine);
            return;
        }
        if (machineStateDetectionSupport.isPaused(status, isWasher)) {
            processPaused(reservation, machine);
            return;
        }
        processRunning(reservation, status, isWasher);
    }

    /**
     * 완료 후보를 디바운스한다. 연속으로 {@code COMPLETION_CONFIRM_THRESHOLD}회 완료로 판정된 경우에만 완료를
     * 확정하여, 사이클 도중 한 번 보고된 순간 정지가 곧바로 완료로 굳어지는 것을 막는다.
     */
    private void processCompletionCandidate(Reservation reservation, Machine machine, CompletionDecision decision) {
        reservation.incrementCompletionCount();
        if (reservation.getCompletionCount() < ReservationConstants.COMPLETION_CONFIRM_THRESHOLD) {
            reservationRepository.save(reservation);
            log.info("completion suspected reservationId={} reason={} count={} threshold={} completionTime={}",
                    reservation.getId(),
                    decision.reason(),
                    reservation.getCompletionCount(),
                    ReservationConstants.COMPLETION_CONFIRM_THRESHOLD,
                    decision.completionTime());
            return;
        }
        completeReservation(reservation, machine, decision.completionTime(), decision.reason());
    }

    private void processInterruption(Reservation reservation, Machine machine) {
        // 사이클 단계 전환 중 순간적으로 보고되는 정지를 진짜 중단으로 오판하지 않도록, 연속으로 중단이
        // 감지될 때만 취소를 확정한다.
        reservation.incrementInterruptionCount();
        if (reservation.getInterruptionCount() < ReservationConstants.INTERRUPTION_CONFIRM_THRESHOLD) {
            reservationRepository.save(reservation);
            log.warn("Reservation {} interruption suspected count={} threshold={} deferring cancellation",
                    reservation.getId(),
                    reservation.getInterruptionCount(),
                    ReservationConstants.INTERRUPTION_CONFIRM_THRESHOLD);
            return;
        }

        reservation.cancel();
        reservation.clearInterruptionCount();
        machine.markAsAvailable();
        reservationRepository.save(reservation);
        machineRepository.save(machine);

        reservationNotificationSupport.sendInterruption(reservation.getUser(), machine);

        log.warn(
                "Reservation {} cancelled due to confirmed machine interruption, no penalty applied (RUNNING → CANCELLED)",
                reservation.getId());
    }

    private void processPaused(Reservation reservation, Machine machine) {
        if (reservation.getInterruptionCount() > 0) {
            reservation.clearInterruptionCount();
            reservationRepository.save(reservation);
        }
        if (reservation.getPausedAt() == null) {
            reservation.markAsPaused();
            reservationRepository.save(reservation);
            log.info("Reservation {} pause started, tracking pause time", reservation.getId());
            return;
        }
        if (Duration.between(reservation.getPausedAt(), DateTimeUtil.nowInKorea())
                .toMinutes() < ReservationConstants.PAUSE_TIMEOUT_MINUTES) {
            return;
        }

        reservation.cancel();
        reservation.clearPausedAt();
        machine.markAsAvailable();
        reservationRepository.save(reservation);
        machineRepository.save(machine);

        reservationNotificationSupport.sendPauseTimeout(reservation.getUser(), machine);

        log.warn("Reservation {} cancelled due to prolonged pause ({}min+), no penalty applied (RUNNING → CANCELLED)",
                reservation.getId(),
                ReservationConstants.PAUSE_TIMEOUT_MINUTES);
    }

    /**
     * 기기가 정상 진행 중일 때 디바운스 카운터와 일시정지 추적을 정리하고, 기기가 보고한 완료 예정 시각을 반영한다. 상한을 벗어난 이상치는
     * 엔티티가 거부하므로 조기 완료 판정의 기준선이 오염되지 않는다.
     */
    private void processRunning(Reservation reservation, SmartThingsDeviceStatusResDto status, boolean isWasher) {
        var changed = false;
        if (reservation.getInterruptionCount() > 0) {
            reservation.clearInterruptionCount();
            changed = true;
        }
        if (reservation.getPausedAt() != null) {
            reservation.clearPausedAt();
            changed = true;
            log.info("Reservation {} resumed from pause, clearing pause tracking", reservation.getId());
        }

        var reportedCompletionTime = DateTimeUtil.parseAndConvertToKoreaTime(getCompletionTime(status, isWasher));
        var current = reservation.getExpectedCompletionTime();
        if (reportedCompletionTime != null
                && (current == null || Math.abs(Duration.between(current, reportedCompletionTime).toSeconds()) >= 60)) {
            if (reservation.updateExpectedCompletionTime(reportedCompletionTime)) {
                changed = true;
            } else {
                log.warn(
                        "expected completion time rejected reservationId={} startTime={} reported={} current={} "
                                + "maxCycleMinutes={}",
                        reservation.getId(),
                        reservation.getStartTime(),
                        reportedCompletionTime,
                        current,
                        ReservationConstants.MAX_REASONABLE_CYCLE_MINUTES);
            }
        }

        if (changed) {
            reservationRepository.save(reservation);
        }
    }

    private void completeReservation(Reservation reservation,
            Machine machine,
            LocalDateTime completionTime,
            String reason) {
        reservation.complete();
        reservation.clearCompletionCount();
        machine.markAsAvailable();
        reservationRepository.save(reservation);
        machineRepository.save(machine);

        reservationNotificationSupport.sendCompletion(reservation.getUser(), machine);

        log.info(
                "Reservation completed reason={} reservationId={} userId={} machineId={} machineName={} completionTime={} expectedCompletionTime={}",
                reason,
                reservation.getId(),
                reservation.getUser().getId(),
                machine.getId(),
                machine.getName(),
                completionTime,
                reservation.getExpectedCompletionTime());
    }

    private void clearCompletionCount(Reservation reservation) {
        if (reservation.getCompletionCount() > 0) {
            reservation.clearCompletionCount();
            reservationRepository.save(reservation);
        }
    }

    private void logCompletionDeferred(Reservation reservation, String reason, LocalDateTime completionTime) {
        log.info(
                "completion deferred reason={} reservationId={} startTime={} expectedCompletionTime={} completionTime={}",
                reason,
                reservation.getId(),
                reservation.getStartTime(),
                reservation.getExpectedCompletionTime(),
                completionTime);
    }

    private String getCompletionTime(SmartThingsDeviceStatusResDto status, boolean isWasher) {
        if (status == null) {
            return null;
        }
        return isWasher ? status.getWasherCompletionTime() : status.getDryerCompletionTime();
    }
}
