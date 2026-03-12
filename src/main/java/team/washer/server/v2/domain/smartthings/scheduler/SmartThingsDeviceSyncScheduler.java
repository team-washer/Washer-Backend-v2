package team.washer.server.v2.domain.smartthings.scheduler;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import team.washer.server.v2.domain.smartthings.service.SyncSmartThingsDevicesService;

/**
 * SmartThings 기기 목록 자동 동기화 스케줄러 매일 오후 3시에 SmartThings API를 호출하여 Machine DB를 동기화
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SmartThingsDeviceSyncScheduler {

    private final SyncSmartThingsDevicesService syncDevicesService;

    @Scheduled(cron = "10 45 8 * * *")
    public void syncDevices() {
        try {
            log.info("SmartThings 기기 목록 동기화 스케줄러 시작");
            syncDevicesService.execute();
            log.info("SmartThings 기기 목록 동기화 스케줄러 완료");
        } catch (Exception e) {
            log.error("SmartThings 기기 목록 동기화 스케줄러 실패", e);
        }
    }
}
