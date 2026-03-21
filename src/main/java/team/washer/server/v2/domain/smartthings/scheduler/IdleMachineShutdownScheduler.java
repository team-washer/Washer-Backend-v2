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

    private static final long SHUTDOWN_CHECK_INTERVAL = 10000;

    private final ShutdownIdleMachinesService shutdownIdleMachinesService;
    private final SmartThingsOperationTimePolicy operationTimePolicy;

    @Scheduled(fixedRate = SHUTDOWN_CHECK_INTERVAL)
    public void shutdownIdleMachines() {
        if (!operationTimePolicy.isOperationAllowed()) {
            log.debug("idle shutdown skipped outside operation hours");
            return;
        }
        try {
            shutdownIdleMachinesService.execute();
        } catch (Exception e) {
            log.error("idle shutdown check failed", e);
        }
    }
}
