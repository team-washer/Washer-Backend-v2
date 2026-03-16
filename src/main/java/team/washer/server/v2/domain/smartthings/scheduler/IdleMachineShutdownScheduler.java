package team.washer.server.v2.domain.smartthings.scheduler;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import team.washer.server.v2.domain.smartthings.service.ShutdownIdleMachinesService;
import team.washer.server.v2.global.thirdparty.smartthings.SmartThingsOperationTimePolicy;

@Component
@RequiredArgsConstructor
@Slf4j
public class IdleMachineShutdownScheduler {

    private static final long SHUTDOWN_CHECK_INTERVAL = 60000;

    private final ShutdownIdleMachinesService shutdownIdleMachinesService;
    private final SmartThingsOperationTimePolicy operationTimePolicy;

    @Scheduled(fixedRate = SHUTDOWN_CHECK_INTERVAL)
    public void shutdownIdleMachines() {
        if (!operationTimePolicy.isOperationAllowed()) {
            log.debug("운영 시간 외 - 유휴 기기 종료 스케줄러 건너뜀");
            return;
        }
        try {
            log.debug("Starting idle machine shutdown check");
            shutdownIdleMachinesService.execute();
            log.debug("Idle machine shutdown check completed");
        } catch (Exception e) {
            log.error("Idle machine shutdown check failed", e);
        }
    }
}
