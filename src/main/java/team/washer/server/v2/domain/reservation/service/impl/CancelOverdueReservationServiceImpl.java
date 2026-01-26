package team.washer.server.v2.domain.reservation.service.impl;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import team.washer.server.v2.domain.reservation.entity.Reservation;
import team.washer.server.v2.domain.reservation.enums.ReservationStatus;
import team.washer.server.v2.domain.reservation.repository.ReservationRepository;
import team.washer.server.v2.domain.reservation.service.CancelOverdueReservationService;
import team.washer.server.v2.domain.reservation.util.PenaltyRedisUtil;
import team.washer.server.v2.domain.smartthings.service.DetectMachineRunningService;
import team.washer.server.v2.domain.smartthings.service.QueryDeviceStatusService;
import team.washer.server.v2.domain.user.entity.User;
import team.washer.server.v2.global.util.DateTimeUtil;

@Slf4j
@Service
@RequiredArgsConstructor
public class CancelOverdueReservationServiceImpl implements CancelOverdueReservationService {

    private final ReservationRepository reservationRepository;
    private final PenaltyRedisUtil penaltyRedisUtil;
    private final DetectMachineRunningService detectMachineRunningService;
    private final QueryDeviceStatusService queryDeviceStatusService;

    @Override
    @Transactional
    public void execute() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(5);
        LocalDateTime recentCutoff = LocalDateTime.now().minusHours(24);

        List<Reservation> expiredReservations = reservationRepository
                .findExpiredReservations(ReservationStatus.RESERVED, threshold, recentCutoff);

        if (expiredReservations.isEmpty()) {
            return;
        }

        log.info("Found {} expired RESERVED reservations", expiredReservations.size());

        for (Reservation reservation : expiredReservations) {
            try {
                var machine = reservation.getMachine();
                var isRunning = detectMachineRunningService.execute(machine.getDeviceId());

                if (isRunning) {
                    var expectedCompletionTime = DateTimeUtil.getExpectedCompletionTime(queryDeviceStatusService,
                            machine.getDeviceId());
                    reservation.start(expectedCompletionTime);
                    reservationRepository.save(reservation);

                    log.info("Expired RESERVED reservation {} auto-started because machine is running",
                            reservation.getId());
                } else {
                    reservation.cancel();
                    reservationRepository.save(reservation);
                    User user = reservation.getUser();
                    penaltyRedisUtil.applyPenalty(user);

                    log.info("Cancelled RESERVED reservation {} due to timeout and applied penalty to user {}",
                            reservation.getId(),
                            user.getId());
                }
            } catch (Exception e) {
                log.error("Error processing timeout for reservation {}", reservation.getId(), e);
            }
        }
    }
}
