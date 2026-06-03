package team.washer.server.v2.domain.smartthings.service.impl;

import java.time.Instant;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import team.themoment.sdk.exception.ExpectedException;
import team.washer.server.v2.domain.smartthings.dto.request.TriggerDeviceSyncReqDto;
import team.washer.server.v2.domain.smartthings.dto.response.DeviceSyncTriggerResDto;
import team.washer.server.v2.domain.smartthings.service.SyncSmartThingsDevicesService;
import team.washer.server.v2.domain.smartthings.service.TriggerManualDeviceSyncService;

/**
 * 기기 목록 수동 동기화 촉발 서비스 구현체
 *
 * <p>
 * 확인 키 값이 {@code true}인지 검증한 뒤, 즉시 폭주를 완충하기 위해 3초 대기한 후 실제 동기화를
 * {@link SyncSmartThingsDevicesService}에 위임합니다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TriggerManualDeviceSyncServiceImpl implements TriggerManualDeviceSyncService {

    private static final long ACCEPTANCE_DELAY_MS = 3000L;

    private final SyncSmartThingsDevicesService syncSmartThingsDevicesService;

    @Override
    public DeviceSyncTriggerResDto execute(final TriggerDeviceSyncReqDto request) {
        if (request.confirmed() == null || !request.confirmed()) {
            log.warn("manual device sync rejected confirmed={}", request.confirmed());
            throw new ExpectedException("실행 확인 값이 올바르지 않습니다. 확인 키의 값으로 true 를 보내야 합니다.", HttpStatus.BAD_REQUEST);
        }

        log.info("manual device sync requested delayMs={}", ACCEPTANCE_DELAY_MS);
        waitBeforeExecution();

        syncSmartThingsDevicesService.execute();
        final var executedAt = Instant.now();
        log.info("manual device sync executed executedAt={}", executedAt);

        return new DeviceSyncTriggerResDto("기기 목록 동기화를 실행했습니다.", executedAt);
    }

    /**
     * 접수 후 실제 실행까지 3초간 대기합니다.
     */
    private void waitBeforeExecution() {
        try {
            Thread.sleep(ACCEPTANCE_DELAY_MS);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("manual device sync interrupted during delay", e);
            throw new ExpectedException("동기화 대기 중 인터럽트가 발생했습니다.", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}
