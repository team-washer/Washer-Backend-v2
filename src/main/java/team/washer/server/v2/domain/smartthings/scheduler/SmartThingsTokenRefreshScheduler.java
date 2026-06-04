package team.washer.server.v2.domain.smartthings.scheduler;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import team.washer.server.v2.domain.smartthings.service.RefreshSmartThingsTokenService;

/**
 * SmartThings 토큰 자동 갱신 스케줄러 약 19분마다 토큰 갱신을 시도하여 토큰 만료로 인한 서비스 중단 방지
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SmartThingsTokenRefreshScheduler {

    private static final long REFRESH_INTERVAL = 19 * 60 * 1000; // 19분마다 갱신

    private final RefreshSmartThingsTokenService refreshTokenService;

    @Scheduled(fixedRate = REFRESH_INTERVAL, initialDelay = 60000)
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
