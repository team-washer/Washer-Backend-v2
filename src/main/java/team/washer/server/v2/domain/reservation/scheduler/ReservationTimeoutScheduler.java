package team.washer.server.v2.domain.reservation.scheduler;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import team.washer.server.v2.domain.reservation.service.CancelOverdueConfirmedReservationService;
import team.washer.server.v2.domain.reservation.service.CancelOverdueReservationService;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReservationTimeoutScheduler {

    private final CancelOverdueReservationService cancelOverdueReservationService;
    private final CancelOverdueConfirmedReservationService cancelOverdueConfirmedReservationService;

    @Scheduled(fixedRate = 10000)
    public void checkReservationTimeouts() {
        try {
            cancelOverdueReservationService.execute();
        } catch (Exception e) {
            log.error("Error checking reservation timeouts", e);
        }
        try {
            cancelOverdueConfirmedReservationService.execute();
        } catch (Exception e) {
            log.error("CONFIRMED 예약 타임아웃 확인 중 오류 발생", e);
        }
    }
}
