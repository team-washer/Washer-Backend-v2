package team.washer.server.v2.domain.smartthings.scheduler;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import team.washer.server.v2.domain.smartthings.service.ShutdownIdleMachinesService;

@Component
@RequiredArgsConstructor
@Slf4j
public class IdleMachineShutdownScheduler {

    private static final long SHUTDOWN_CHECK_INTERVAL = 2000;

    private final ShutdownIdleMachinesService shutdownIdleMachinesService;

    @Scheduled(fixedRate = SHUTDOWN_CHECK_INTERVAL)
    public void shutdownIdleMachines() {
        try {
            log.debug("Starting idle machine shutdown check");
            shutdownIdleMachinesService.execute();
            log.debug("Idle machine shutdown check completed");
        } catch (Exception e) {
            log.error("Idle machine shutdown check failed", e);
        }
    }
}
