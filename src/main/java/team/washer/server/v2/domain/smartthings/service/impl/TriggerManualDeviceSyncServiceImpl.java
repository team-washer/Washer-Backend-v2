package team.washer.server.v2.domain.smartthings.service.impl;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import team.themoment.sdk.exception.ExpectedException;
import team.washer.server.v2.domain.smartthings.dto.request.TriggerDeviceSyncReqDto;
import team.washer.server.v2.domain.smartthings.dto.response.DeviceSyncTriggerResDto;
import team.washer.server.v2.domain.smartthings.service.TriggerManualDeviceSyncService;
import team.washer.server.v2.domain.smartthings.support.DeviceSyncCoalescer;

/**
 * 기기 목록 수동 동기화 촉발 서비스 구현체
 *
 * <p>
 * 확인 키 값이 {@code true}인지 검증한 뒤, HTTP 스레드를 블로킹하지 않고 {@link DeviceSyncCoalescer}에
 * 요청을 접수한다. 실제 동기화는 5초 창 안의 요청을 통합하여 백그라운드에서 한 번만 실행된다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TriggerManualDeviceSyncServiceImpl implements TriggerManualDeviceSyncService {

    private final DeviceSyncCoalescer deviceSyncCoalescer;

    @Override
    public DeviceSyncTriggerResDto execute(final TriggerDeviceSyncReqDto request) {
        if (request.confirmed() == null || !request.confirmed()) {
            log.warn("manual device sync rejected confirmed={}", request.confirmed());
            throw new ExpectedException("실행 확인 값이 올바르지 않습니다. 확인 키의 값으로 true 를 보내야 합니다.", HttpStatus.BAD_REQUEST);
        }

        final var submission = deviceSyncCoalescer.submit();
        log.info("manual device sync accepted newlyScheduled={} scheduledAt={} pendingRequestCount={}",
                submission.newlyScheduled(),
                submission.scheduledAt(),
                submission.pendingRequestCount());

        final var message = submission.newlyScheduled()
                ? "기기 목록 동기화를 예약했습니다. 약 5초 후 실행됩니다."
                : "이미 예약된 동기화 요청에 통합되었습니다.";

        return new DeviceSyncTriggerResDto(message,
                submission.newlyScheduled(),
                submission.scheduledAt(),
                submission.pendingRequestCount());
    }
}
