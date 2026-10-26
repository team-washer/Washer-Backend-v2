package team.washer.server.v2.domain.smartthings.scheduler;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import team.washer.server.v2.domain.smartthings.service.CleanupSmartThingsDeviceEventsService;

/**
 * SmartThings webhook event 보존 기간 정리 스케줄러. 매일 새벽 4시 30분에 실행한다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SmartThingsDeviceEventCleanupScheduler {

    private final CleanupSmartThingsDeviceEventsService cleanupService;

    @Scheduled(cron = "0 30 4 * * *", zone = "Asia/Seoul")
    public void cleanup() {
        try {
            cleanupService.execute();
        } catch (Exception e) {
            log.error("SmartThings device event cleanup failed", e);
        }
    }
}
