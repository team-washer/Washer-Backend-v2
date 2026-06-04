package team.washer.server.v2.domain.reservation.scheduler;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import team.washer.server.v2.domain.reservation.service.CancelOverdueReservationService;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReservationTimeoutScheduler {

    private static final long TIMEOUT_CHECK_INTERVAL = 60000;

    private final CancelOverdueReservationService cancelOverdueReservationService;

    @Scheduled(fixedDelay = TIMEOUT_CHECK_INTERVAL)
    public void checkReservationTimeouts() {
        try {
            cancelOverdueReservationService.execute();
        } catch (Exception e) {
            log.error("reservation timeout check failed for RESERVED", e);
        }
    }
}
