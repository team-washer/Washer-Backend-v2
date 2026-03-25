package team.washer.server.v2.domain.reservation.service.impl;

import java.time.Duration;
import java.time.LocalDateTime;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import team.washer.server.v2.domain.machine.repository.MachineRepository;
import team.washer.server.v2.domain.notification.service.SendCompletionNotificationService;
import team.washer.server.v2.domain.notification.service.SendInterruptionNotificationService;
import team.washer.server.v2.domain.notification.service.SendPauseTimeoutNotificationService;
import team.washer.server.v2.domain.reservation.enums.ReservationStatus;
import team.washer.server.v2.domain.reservation.repository.ReservationRepository;
import team.washer.server.v2.domain.reservation.service.ProcessReservationLifecycleService;
import team.washer.server.v2.domain.smartthings.service.DetectMachineCompletionService;
import team.washer.server.v2.domain.smartthings.service.DetectMachineInterruptedService;
import team.washer.server.v2.domain.smartthings.service.DetectMachinePausedService;
import team.washer.server.v2.domain.smartthings.service.DetectMachineRunningService;
import team.washer.server.v2.domain.smartthings.service.QueryDeviceStatusService;
import team.washer.server.v2.global.common.constants.ReservationConstants;
import team.washer.server.v2.global.util.DateTimeUtil;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProcessReservationLifecycleServiceImpl implements ProcessReservationLifecycleService {

    private final ReservationRepository reservationRepository;
    private final MachineRepository machineRepository;
    private final DetectMachineRunningService detectMachineRunningService;
    private final DetectMachineCompletionService detectMachineCompletionService;
    private final DetectMachineInterruptedService detectMachineInterruptedService;
    private final DetectMachinePausedService detectMachinePausedService;
    private final QueryDeviceStatusService queryDeviceStatusService;
    private final SendCompletionNotificationService sendCompletionNotificationService;
    private final SendInterruptionNotificationService sendInterruptionNotificationService;
    private final SendPauseTimeoutNotificationService sendPauseTimeoutNotificationService;

    @Override
    public void execute() {
        processConfirmedToRunning();
        processRunningToCompleted();
    }

    private void processConfirmedToRunning() {
        var confirmedReservations = reservationRepository.findByStatusWithMachineAndUser(ReservationStatus.CONFIRMED);

        for (var reservation : confirmedReservations) {
            try {
                var machine = reservation.getMachine();
                var isRunning = detectMachineRunningService.execute(machine.getDeviceId());

                if (isRunning) {
                    var expectedCompletionTime = DateTimeUtil.getExpectedCompletionTime(queryDeviceStatusService,
                            machine.getDeviceId());
                    reservation.start(expectedCompletionTime);
                    machine.markAsInUse();
                    reservationRepository.save(reservation);
                    machineRepository.save(machine);

                    log.info("Reservation {} started (CONFIRMED → RUNNING)", reservation.getId());
                }
            } catch (Exception e) {
                log.error("Failed to process CONFIRMED reservation: {}", reservation.getId(), e);
            }
        }
    }

    private void processRunningToCompleted() {
        var runningReservations = reservationRepository.findByStatusWithMachineAndUser(ReservationStatus.RUNNING);

        for (var reservation : runningReservations) {
            try {
                var machine = reservation.getMachine();
                var completionTime = detectMachineCompletionService.execute(machine.getDeviceId());

                if (completionTime.isPresent()) {
                    reservation.complete();
                    machine.markAsAvailable();
                    reservationRepository.save(reservation);
                    machineRepository.save(machine);

                    sendCompletionNotificationService.execute(reservation.getUser(), machine);

                    log.info("Reservation {} completed (RUNNING → COMPLETED)", reservation.getId());
                } else if (detectMachineInterruptedService.execute(machine.getDeviceId())) {
                    reservation.cancel();
                    machine.markAsAvailable();
                    reservationRepository.save(reservation);
                    machineRepository.save(machine);

                    sendInterruptionNotificationService.execute(reservation.getUser(), machine);

                    log.warn(
                            "Reservation {} cancelled due to machine interruption, no penalty applied (RUNNING → CANCELLED)",
                            reservation.getId());
                } else if (detectMachinePausedService.execute(machine.getDeviceId())) {
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

                        sendPauseTimeoutNotificationService.execute(reservation.getUser(), machine);

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
                    var updatedExpectedCompletionTime = DateTimeUtil.getExpectedCompletionTime(queryDeviceStatusService,
                            machine.getDeviceId());
                    reservation.updateExpectedCompletionTime(updatedExpectedCompletionTime);
                    reservationRepository.save(reservation);
                }
            } catch (Exception e) {
                log.error("Failed to process RUNNING reservation: {}", reservation.getId(), e);
            }
        }
    }
}
