package team.washer.server.v2.domain.reservation.scheduler;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import team.washer.server.v2.domain.reservation.service.CancelOverdueReservationService;
import team.washer.server.v2.global.thirdparty.smartthings.SmartThingsOperationTimePolicy;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReservationTimeoutScheduler {

    private static final long TIMEOUT_CHECK_INTERVAL = 60000;

    private final CancelOverdueReservationService cancelOverdueReservationService;
    private final SmartThingsOperationTimePolicy operationTimePolicy;

    /**
     * RESERVED 예약의 타임아웃을 확인한다.
     *
     * <p>
     * 운영 시간 외에는 라이프사이클 스케줄러가 멈춰 있어 RESERVED → RUNNING 전환이 일어나지 않는다. 그 시간대에 타임아웃만 계속
     * 돌면 예약이 자동 시작 기회 없이 취소되고 패널티까지 부과되므로, 다른 스케줄러와 동일하게 운영 시간을 확인한다.
     */
    @Scheduled(fixedDelay = TIMEOUT_CHECK_INTERVAL)
    public void checkReservationTimeouts() {
        if (!operationTimePolicy.isOperationAllowed()) {
            log.debug("reservation timeout check skipped outside operation hours");
            return;
        }
        try {
            cancelOverdueReservationService.execute();
        } catch (Exception e) {
            log.error("reservation timeout check failed for RESERVED", e);
        }
    }
}
