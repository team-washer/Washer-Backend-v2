package team.washer.server.v2.domain.reservation.service.impl;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import team.washer.server.v2.domain.notification.service.SendCompletionNotificationService;
import team.washer.server.v2.domain.reservation.enums.ReservationStatus;
import team.washer.server.v2.domain.reservation.repository.ReservationRepository;
import team.washer.server.v2.domain.reservation.service.ProcessReservationLifecycleService;
import team.washer.server.v2.domain.smartthings.service.DetectMachineCompletionService;
import team.washer.server.v2.domain.smartthings.service.DetectMachineRunningService;
import team.washer.server.v2.domain.smartthings.service.QueryDeviceStatusService;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProcessReservationLifecycleServiceImpl implements ProcessReservationLifecycleService {

    private final ReservationRepository reservationRepository;
    private final DetectMachineRunningService detectMachineRunningService;
    private final DetectMachineCompletionService detectMachineCompletionService;
    private final QueryDeviceStatusService queryDeviceStatusService;
    private final SendCompletionNotificationService sendCompletionNotificationService;

    @Override
    @Transactional
    public void execute() {
        processConfirmedToRunning();
        processRunningToCompleted();
    }

    private void processConfirmedToRunning() {
        var confirmedReservations = reservationRepository.findByStatus(ReservationStatus.CONFIRMED);

        for (var reservation : confirmedReservations) {
            try {
                var machine = reservation.getMachine();
                var isRunning = detectMachineRunningService.execute(machine.getDeviceId());

                if (isRunning) {
                    var expectedCompletionTime = getExpectedCompletionTime(machine.getDeviceId());
                    reservation.start(expectedCompletionTime);
                    reservationRepository.save(reservation);

                    log.info("Reservation {} started (CONFIRMED → RUNNING)", reservation.getId());
                }
            } catch (Exception e) {
                log.error("Failed to process CONFIRMED reservation: {}", reservation.getId(), e);
            }
        }
    }

    private void processRunningToCompleted() {
        var runningReservations = reservationRepository.findByStatus(ReservationStatus.RUNNING);

        for (var reservation : runningReservations) {
            try {
                var machine = reservation.getMachine();
                var completionTime = detectMachineCompletionService.execute(machine.getDeviceId());

                if (completionTime.isPresent()) {
                    reservation.complete();
                    reservationRepository.save(reservation);

                    sendCompletionNotificationService.execute(reservation.getUser(), machine);

                    log.info("Reservation {} completed (RUNNING → COMPLETED)", reservation.getId());
                }
            } catch (Exception e) {
                log.error("Failed to process RUNNING reservation: {}", reservation.getId(), e);
            }
        }
    }

    private LocalDateTime getExpectedCompletionTime(String deviceId) {
        try {
            var status = queryDeviceStatusService.execute(deviceId);
            var completionTimeStr = status.getCompletionTime();

            if (completionTimeStr != null && !completionTimeStr.isBlank()) {
                var utcTime = ZonedDateTime.parse(completionTimeStr);
                return utcTime.withZoneSameInstant(ZoneId.of("Asia/Seoul")).toLocalDateTime();
            }
        } catch (Exception e) {
            log.warn("Failed to get expected completion time for device: {}", deviceId, e);
        }

        return LocalDateTime.now().plusHours(1);
    }
}
