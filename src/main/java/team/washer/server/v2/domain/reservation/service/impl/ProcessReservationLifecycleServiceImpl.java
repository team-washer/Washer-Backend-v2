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
                var isRunning = machineStateDetectionSupport.isRunning(status);

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
                var status = deviceStatusQuerySupport.queryDeviceStatus(machine.getDeviceId());
                var completionTime = machineStateDetectionSupport.isCompleted(status);

                if (completionTime.isPresent()) {
                    reservation.complete();
                    machine.markAsAvailable();
                    reservationRepository.save(reservation);
                    machineRepository.save(machine);

                    reservationNotificationSupport.sendCompletion(reservation.getUser(), machine);

                    log.info("Reservation {} completed (RUNNING → COMPLETED)", reservation.getId());
                } else if (machineStateDetectionSupport.isInterrupted(status)) {
                    reservation.cancel();
                    machine.markAsAvailable();
                    reservationRepository.save(reservation);
                    machineRepository.save(machine);

                    reservationNotificationSupport.sendInterruption(reservation.getUser(), machine);

                    log.warn(
                            "Reservation {} cancelled due to machine interruption, no penalty applied (RUNNING → CANCELLED)",
                            reservation.getId());
                } else if (machineStateDetectionSupport.isPaused(status)) {
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
