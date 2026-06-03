package team.washer.server.v2.domain.smartthings.support;

import java.time.Instant;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;
import team.washer.server.v2.domain.smartthings.service.SyncSmartThingsDevicesService;

/**
 * 기기 동기화 요청을 인메모리로 통합(coalesce)하는 단일 실행(single-flight) 큐
 *
 * <p>
 * permitAll로 공개된 수동 동기화 엔드포인트가 블로킹이나 폭주에 취약하지 않도록, HTTP 스레드를 점유하지 않고 요청을 즉시
 * 접수한다. 첫 요청은 5초 뒤 실행을 예약하며, 그 5초 창(window) 안에 도착해 아직 처리되지 않은 후속 요청은 새 실행을 만들지
 * 않고 기존 예약에 통합된다. 창이 만료되어 실행이 시작되면 다음 요청부터 새 창이 열린다.
 */
@Component
@Slf4j
public class DeviceSyncCoalescer {

    private static final long WINDOW_MILLIS = 5000L;

    private final TaskScheduler taskScheduler;
    private final SyncSmartThingsDevicesService syncSmartThingsDevicesService;
    private final Object lock = new Object();

    private PendingWindow pending;

    public DeviceSyncCoalescer(@Qualifier("customTaskScheduler") final TaskScheduler taskScheduler,
            final SyncSmartThingsDevicesService syncSmartThingsDevicesService) {
        this.taskScheduler = taskScheduler;
        this.syncSmartThingsDevicesService = syncSmartThingsDevicesService;
    }

    /**
     * 동기화 요청을 접수한다. 진행 중(미실행)인 창이 있으면 통합하고, 없으면 새 창을 예약한다.
     *
     * @return 신규 예약 여부, 실행 예정 시각, 현재 창에 통합된 누적 요청 수
     */
    public Submission submit() {
        synchronized (lock) {
            if (pending != null) {
                final var count = pending.requestCount().incrementAndGet();
                log.info("device sync coalesced into pending window scheduledAt={} pendingRequestCount={}",
                        pending.scheduledAt(),
                        count);
                return new Submission(false, pending.scheduledAt(), count);
            }
            final var scheduledAt = Instant.now().plusMillis(WINDOW_MILLIS);
            final var requestCount = new AtomicInteger(1);
            final ScheduledFuture<?> future = taskScheduler.schedule(this::runWindow, scheduledAt);
            pending = new PendingWindow(scheduledAt, requestCount, future);
            log.info("device sync window scheduled scheduledAt={} windowMs={}", scheduledAt, WINDOW_MILLIS);
            return new Submission(true, scheduledAt, 1);
        }
    }

    /**
     * 창이 만료되어 실행되는 시점. 현재 창을 닫아 이후 요청은 새 창을 열도록 하고, 통합된 요청 전체에 대해 동기화를 한 번만 실행한다.
     */
    private void runWindow() {
        final int totalRequests;
        synchronized (lock) {
            totalRequests = pending != null ? pending.requestCount().get() : 0;
            pending = null;
        }
        try {
            log.info("device sync window firing coalescedRequestCount={}", totalRequests);
            syncSmartThingsDevicesService.execute();
            log.info("device sync window completed coalescedRequestCount={}", totalRequests);
        } catch (final Exception e) {
            log.error("device sync window failed coalescedRequestCount={}", totalRequests, e);
        }
    }

    private record PendingWindow(Instant scheduledAt, AtomicInteger requestCount, ScheduledFuture<?> future) {
    }

    /**
     * 동기화 요청 접수 결과
     *
     * @param newlyScheduled
     *            새 실행 창을 예약했으면 true, 기존 창에 통합되었으면 false
     * @param scheduledAt
     *            동기화 실행 예정 시각
     * @param pendingRequestCount
     *            현재 창에 통합된 누적 요청 수
     */
    public record Submission(boolean newlyScheduled, Instant scheduledAt, int pendingRequestCount) {
    }
}
