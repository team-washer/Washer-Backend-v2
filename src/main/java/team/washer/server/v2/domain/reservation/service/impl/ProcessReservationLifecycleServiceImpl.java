package team.washer.server.v2.domain.reservation.service.impl;

import java.time.Duration;
import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import team.washer.server.v2.domain.machine.repository.MachineRepository;
import team.washer.server.v2.domain.notification.support.ReservationNotificationSupport;
import team.washer.server.v2.domain.reservation.enums.ReservationStatus;
import team.washer.server.v2.domain.reservation.repository.ReservationRepository;
import team.washer.server.v2.domain.reservation.service.ProcessReservationLifecycleService;
import team.washer.server.v2.domain.smartthings.support.DeviceStatusQuerySupport;
import team.washer.server.v2.domain.smartthings.support.MachineStateDetectionSupport;
import team.washer.server.v2.global.common.constants.ReservationConstants;
import team.washer.server.v2.global.util.DateTimeUtil;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProcessReservationLifecycleServiceImpl implements ProcessReservationLifecycleService {

    private final ReservationRepository reservationRepository;
    private final MachineRepository machineRepository;
    private final MachineStateDetectionSupport machineStateDetectionSupport;
    private final ReservationNotificationSupport reservationNotificationSupport;
    private final DeviceStatusQuerySupport deviceStatusQuerySupport;

    @Override
    @Transactional
    public void execute() {
        processReservedToRunning();
        processRunningToCompleted();
    }

    private void processReservedToRunning() {
        var reservedReservations = reservationRepository.findByStatusWithMachineAndUser(ReservationStatus.RESERVED);

        for (var reservation : reservedReservations) {
            try {
                var machine = reservation.getMachine();
                var status = deviceStatusQuerySupport.queryDeviceStatus(machine.getDeviceId());
                var isRunning = machineStateDetectionSupport.isRunning(status, machine.isWasher());

                if (isRunning) {
                    var expectedCompletionTime = DateTimeUtil.parseAndConvertToKoreaTime(status.getCompletionTime());
                    reservation.start(expectedCompletionTime);
                    machine.markAsInUse();
                    reservationRepository.save(reservation);
                    machineRepository.save(machine);

                    reservationNotificationSupport.sendStarted(reservation.getUser(), machine, expectedCompletionTime);

                    log.info("Reservation {} started (RESERVED → RUNNING)", reservation.getId());
                }
            } catch (Exception e) {
                log.error("Failed to process RESERVED reservation: {}", reservation.getId(), e);
            }
        }
    }

    private void processRunningToCompleted() {
        var runningReservations = reservationRepository.findByStatusWithMachineAndUser(ReservationStatus.RUNNING);

        for (var reservation : runningReservations) {
            try {
                var machine = reservation.getMachine();
                var isWasher = machine.isWasher();
                var status = deviceStatusQuerySupport.queryDeviceStatus(machine.getDeviceId());
                var completionTime = machineStateDetectionSupport.isCompleted(status, isWasher);

                if (completionTime.isPresent()) {
                    reservation.complete();
                    machine.markAsAvailable();
                    reservationRepository.save(reservation);
                    machineRepository.save(machine);

                    reservationNotificationSupport.sendCompletion(reservation.getUser(), machine);

                    log.info("Reservation {} completed (RUNNING → COMPLETED)", reservation.getId());
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
                    }
                    if (reservation.getPausedAt() == null) {
                        reservation.markAsPaused();
                        reservationRepository.save(reservation);
                        log.info("Reservation {} pause started, tracking pause time", reservation.getId());
                    } else if (Duration.between(reservation.getPausedAt(), LocalDateTime.now())
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
                    }
                    if (reservation.getPausedAt() != null) {
                        reservation.clearPausedAt();
                        log.info("Reservation {} resumed from pause, clearing pause tracking", reservation.getId());
                    }
                    var updatedExpectedCompletionTime = DateTimeUtil
                            .parseAndConvertToKoreaTime(status.getCompletionTime());
                    var current = reservation.getExpectedCompletionTime();
                    if (updatedExpectedCompletionTime != null && (current == null
                            || Math.abs(Duration.between(current, updatedExpectedCompletionTime).toSeconds()) >= 60)) {
                        reservation.updateExpectedCompletionTime(updatedExpectedCompletionTime);
                        reservationRepository.save(reservation);
                    }
                }
            } catch (Exception e) {
                log.error("Failed to process RUNNING reservation: {}", reservation.getId(), e);
            }
        }
    }
}
