package team.washer.server.v2.domain.smartthings.scheduler;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import team.washer.server.v2.domain.smartthings.service.RefreshSmartThingsTokenService;

/**
 * SmartThings 토큰 자동 갱신 스케줄러 55분마다 토큰을 갱신하여 만료 방지
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SmartThingsTokenRefreshScheduler {

    private static final long REFRESH_INTERVAL = 55 * 60 * 1000; // 55분

    private final RefreshSmartThingsTokenService refreshTokenService;

    /**
     * SmartThings 토큰 자동 갱신 55분마다 실행되어 토큰 만료 5분 전에 갱신
     */
    @Scheduled(fixedRate = REFRESH_INTERVAL, initialDelay = REFRESH_INTERVAL)
    public void refreshToken() {
        try {
            log.info("Starting SmartThings token refresh scheduler");
            refreshTokenService.execute();
            log.info("SmartThings token refresh scheduler completed successfully");
        } catch (Exception e) {
            log.error("SmartThings token refresh scheduler failed", e);
        }
    }
}
