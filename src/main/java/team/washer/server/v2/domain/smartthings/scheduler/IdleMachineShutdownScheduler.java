package team.washer.server.v2.domain.smartthings.scheduler;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import team.washer.server.v2.domain.reservation.service.CancelOverdueReservationService;
import team.washer.server.v2.domain.smartthings.service.ShutdownIdleMachinesService;
import team.washer.server.v2.global.thirdparty.smartthings.SmartThingsOperationTimePolicy;

/**
 * RESERVED 예약의 타임아웃을 정리한 뒤, 활성 예약이 없는 기기의 전원을 차단한다.
 *
 * <p>
 * 두 작업의 순서가 중요하다. 타임아웃이 지난 RESERVED 예약은 전원 차단 대상 판정에서 활성 예약으로 세지 않으므로, 먼저 정리하지
 * 않으면 늦게 시작한 예약이 구제되기 전에 작동 중인 기기의 전원이 꺼질 수 있다. 타임아웃 정리는 기기가 작동 중이면 예약을
 * RUNNING으로 살리고({@code CancelOverdueReservationService}), 그렇지 않으면 예약을 취소한다. 그
 * 결과를 확정한 뒤 전원을 차단하므로, 꺼지는 기기는 실제로 예약 없이 켜진 기기뿐이다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class IdleMachineShutdownScheduler {

    private static final long SHUTDOWN_CHECK_INTERVAL = 60000;

    private final CancelOverdueReservationService cancelOverdueReservationService;
    private final ShutdownIdleMachinesService shutdownIdleMachinesService;
    private final SmartThingsOperationTimePolicy operationTimePolicy;

    /**
     * 운영 시간 외에는 라이프사이클 스케줄러가 멈춰 있어 RESERVED → RUNNING 전환이 일어나지 않는다. 그 시간대에 타임아웃만 계속
     * 돌면 예약이 자동 시작 기회 없이 취소되고 패널티까지 부과되므로, 운영 시간을 먼저 확인한다.
     */
    @Scheduled(fixedDelay = SHUTDOWN_CHECK_INTERVAL)
    public void shutdownIdleMachines() {
        if (!operationTimePolicy.isOperationAllowed()) {
            log.debug("reservation timeout and idle shutdown skipped outside operation hours");
            return;
        }

        try {
            cancelOverdueReservationService.execute();
        } catch (Exception e) {
            log.error("reservation timeout check failed for RESERVED, skipping idle shutdown", e);
            return;
        }

        try {
            shutdownIdleMachinesService.execute();
        } catch (Exception e) {
            log.error("idle shutdown check failed", e);
        }
    }
}
