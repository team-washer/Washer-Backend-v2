package team.washer.server.v2.domain.reservation.scheduler;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import team.washer.server.v2.domain.reservation.service.ProcessReservationLifecycleService;
import team.washer.server.v2.global.thirdparty.smartthings.SmartThingsOperationTimePolicy;

@Component
@RequiredArgsConstructor
@Slf4j
public class ReservationLifecycleScheduler {

    private static final long LIFECYCLE_CHECK_INTERVAL = 10000;

    private final ProcessReservationLifecycleService processReservationLifecycleService;
    private final SmartThingsOperationTimePolicy operationTimePolicy;

    @Scheduled(fixedDelay = LIFECYCLE_CHECK_INTERVAL)
    public void processLifecycle() {
        if (!operationTimePolicy.isOperationAllowed()) {
            log.debug("운영 시간 외 - 예약 라이프사이클 스케줄러 건너뜀");
            return;
        }
        try {
            log.debug("Starting reservation lifecycle processing");
            processReservationLifecycleService.execute();
            log.debug("Reservation lifecycle processing completed");
        } catch (Exception e) {
            log.error("Reservation lifecycle processing failed", e);
        }
    }
}
