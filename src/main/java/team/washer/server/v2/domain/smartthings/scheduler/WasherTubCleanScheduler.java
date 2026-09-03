package team.washer.server.v2.domain.smartthings.scheduler;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import team.washer.server.v2.domain.smartthings.service.ReleaseFinishedWasherTubCleanService;
import team.washer.server.v2.domain.smartthings.service.RunWasherTubCleanService;

/**
 * 매주 금요일 오전 10시에 무세제 통세척을 실행하고 완료된 기기의 점유를 해제한다.
 */
@Component
@ConditionalOnProperty(prefix = "third-party.smartthings.tub-clean", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class WasherTubCleanScheduler {

    private static final long COMPLETION_CHECK_INTERVAL = 60000;

    private final RunWasherTubCleanService runWasherTubCleanService;
    private final ReleaseFinishedWasherTubCleanService releaseFinishedWasherTubCleanService;

    @Scheduled(cron = "${third-party.smartthings.tub-clean.cron:0 0 10 * * FRI}", zone = "Asia/Seoul")
    public void runTubClean() {
        try {
            runWasherTubCleanService.execute();
        } catch (Exception e) {
            log.error("washer tub clean scheduler failed", e);
        }
    }

    @Scheduled(fixedDelay = COMPLETION_CHECK_INTERVAL)
    public void releaseFinishedTubClean() {
        try {
            releaseFinishedWasherTubCleanService.execute();
        } catch (Exception e) {
            log.error("washer tub clean completion check failed", e);
        }
    }
}
