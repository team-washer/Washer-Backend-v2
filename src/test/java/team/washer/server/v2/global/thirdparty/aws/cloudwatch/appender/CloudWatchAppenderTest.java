package team.washer.server.v2.global.thirdparty.aws.cloudwatch.appender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import java.util.stream.IntStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxy;
import software.amazon.awssdk.services.cloudwatchlogs.CloudWatchLogsClient;
import software.amazon.awssdk.services.cloudwatchlogs.model.PutLogEventsRequest;
import software.amazon.awssdk.services.cloudwatchlogs.model.PutLogEventsResponse;
import team.washer.server.v2.global.common.trace.TraceIdFilter;

@DisplayName("CloudWatch Appender는")
class CloudWatchAppenderTest {

    @Nested
    @DisplayName("예외 payload는")
    class ExceptionPayload {

        @Test
        @DisplayName("예외 원인과 허용된 추적 문맥을 비동기 전송 payload에 보존한다")
        void preservesExceptionAndTraceContextInPayload() {
            // Given
            final var client = mock(CloudWatchLogsClient.class);
            final var appender = appender(client);
            final var event = loggingEvent("request failed",
                    new IllegalStateException(
                            "root cause Authorization: Basic dXNlcjpwYXNz, password=\"correct horse battery staple\", "
                                    + "access_token=access-secret {\"refresh_token\":\"refresh-secret\"}"),
                    Map.of(TraceIdFilter.MDC_KEY, "trace-123", "lifecycleRunId", "lifecycle-run-123"));

            // When
            ReflectionTestUtils.invokeMethod(appender, "flushBatch", List.of(event));

            // Then
            final var requestCaptor = ArgumentCaptor.forClass(PutLogEventsRequest.class);
            verify(client).putLogEvents(requestCaptor.capture());
            final var message = requestCaptor.getValue().logEvents().getFirst().message();
            assertThat(message).contains("level=\"ERROR\"").contains("logger=\"test.Logger\"")
                    .contains("traceId=\"trace-123\"").contains("exceptionType=\"java.lang.IllegalStateException\"")
                    .contains("lifecycleRunId=\"lifecycle-run-123\"").contains("root cause")
                    .doesNotContain("dXNlcjpwYXNz").doesNotContain("correct horse battery staple")
                    .doesNotContain("access-secret").doesNotContain("refresh-secret")
                    .contains("Authorization: [REDACTED]").contains("password=\\\"[REDACTED]\\\"")
                    .contains("access_token=[REDACTED]").contains("{\\\"refresh_token\\\":\\\"[REDACTED]\\\"}");
        }

        @Test
        @DisplayName("긴 예외 추적 정보는 설정된 길이에서 잘라낸다")
        void truncatesLongThrowable() {
            // Given
            final var client = mock(CloudWatchLogsClient.class);
            final var appender = appender(client);
            appender.setMaxThrowableLength(40);
            final var event = loggingEvent("failed", new IllegalStateException("a very long root cause"), Map.of());

            // When
            ReflectionTestUtils.invokeMethod(appender, "flushBatch", List.of(event));

            // Then
            final var requestCaptor = ArgumentCaptor.forClass(PutLogEventsRequest.class);
            verify(client).putLogEvents(requestCaptor.capture());
            assertThat(requestCaptor.getValue().logEvents().getFirst().message()).contains("...[truncated]");
        }

        @Test
        @DisplayName("긴 stack trace에서도 원인 체인을 앞쪽에 보존한다")
        void preservesCauseChainBeforeTruncation() {
            // Given
            final var client = mock(CloudWatchLogsClient.class);
            final var appender = appender(client);
            appender.setMaxThrowableLength(120);
            final var event = loggingEvent("failed",
                    new RuntimeException("outer".repeat(500), new IllegalArgumentException("root-cause")),
                    Map.of());

            // When
            ReflectionTestUtils.invokeMethod(appender, "flushBatch", List.of(event));

            // Then
            final var requestCaptor = ArgumentCaptor.forClass(PutLogEventsRequest.class);
            verify(client).putLogEvents(requestCaptor.capture());
            assertThat(requestCaptor.getValue().logEvents().getFirst().message()).contains("root-cause")
                    .contains("...[truncated]");
        }
    }

    @Nested
    @DisplayName("CloudWatch 배치는")
    class BatchSize {

        @Test
        @DisplayName("UTF-8 바이트 기준 1MB를 넘지 않도록 여러 요청으로 나눈다")
        void splitsBatchByUtf8Bytes() {
            // Given
            final var client = mock(CloudWatchLogsClient.class);
            final var appender = appender(client);
            appender.setMaxBatchSize(50);
            final var events = IntStream.range(0, 40)
                    .mapToObj(index -> loggingEvent("가".repeat(10_000), null, Map.of())).toList();

            // When
            ReflectionTestUtils.invokeMethod(appender, "flushBatch", events);

            // Then
            final var requestCaptor = ArgumentCaptor.forClass(PutLogEventsRequest.class);
            verify(client, times(2)).putLogEvents(requestCaptor.capture());
            assertThat(requestCaptor.getAllValues()).allSatisfy(request -> assertThat(request.logEvents()).isNotEmpty()
                    .allSatisfy(event -> assertThat(event.message().getBytes(StandardCharsets.UTF_8).length + 26)
                            .isLessThanOrEqualTo(1_048_576)));
            assertThat(requestCaptor.getAllValues().stream().mapToInt(request -> request.logEvents().size()).sum())
                    .isEqualTo(40);
        }
    }

    @Nested
    @DisplayName("큐 포화 처리는")
    class QueueSaturation {

        @Test
        @DisplayName("전송이 멈춘 상태에서도 큐 크기가 설정한 상한을 넘지 않는다")
        void keepsQueueWithinConfiguredCapacity() {
            // Given
            final var appender = startedAppender(mock(CloudWatchLogsClient.class), 10);

            // When
            IntStream.range(0, 500).forEach(index -> appender.append(event(Level.INFO, "burst index=" + index)));

            // Then
            assertThat(appender.getQueueSize()).isLessThanOrEqualTo(10);
            assertThat(appender.getDiscardedLowPriorityCount()).isPositive();
            assertThat(appender.getDiscardedHighPriorityCount()).isZero();
        }

        @Test
        @DisplayName("포화 시 낮은 중요도 로그를 먼저 드롭하고 ERROR는 큐에 보존한다")
        void dropsLowPriorityFirstAndKeepsErrors() {
            // Given
            final var appender = startedAppender(mock(CloudWatchLogsClient.class), 10);
            IntStream.range(0, 50).forEach(index -> appender.append(event(Level.INFO, "info index=" + index)));

            // When
            IntStream.range(0, 5).forEach(index -> appender.append(event(Level.ERROR, "error index=" + index)));

            // Then
            assertThat(appender.getQueueSize()).isLessThanOrEqualTo(10);
            assertThat(levelsInQueue(appender)).filteredOn(Level.ERROR::equals).hasSize(5);
            assertThat(appender.getEvictedLowPriorityCount()).isPositive();
            assertThat(appender.getDiscardedHighPriorityCount()).isZero();
            assertThat(appender.getSaturationCount()).isPositive();
        }

        @Test
        @DisplayName("ERROR만 가득 찬 큐에서는 ERROR도 드롭하고 요청 스레드를 차단하지 않는다")
        void dropsErrorWithoutBlockingWhenQueueIsFullOfErrors() {
            // Given
            final var appender = startedAppender(mock(CloudWatchLogsClient.class), 5);
            appender.setMaxBlockTimeMillis(0);
            IntStream.range(0, 5).forEach(index -> appender.append(event(Level.ERROR, "error index=" + index)));

            // When
            final var startedAt = System.currentTimeMillis();
            appender.append(event(Level.ERROR, "overflow error"));
            final var elapsed = System.currentTimeMillis() - startedAt;

            // Then
            assertThat(appender.getQueueSize()).isEqualTo(5);
            assertThat(appender.getDiscardedHighPriorityCount()).isEqualTo(1);
            assertThat(elapsed).isLessThan(500);
        }

        @Test
        @DisplayName("드롭·포화 수치를 다음 배치의 요약 이벤트로 전송한다")
        void reportsDropStatisticsAsSummaryEvent() {
            // Given
            final var client = mock(CloudWatchLogsClient.class);
            final var appender = startedAppender(client, 2);
            IntStream.range(0, 20).forEach(index -> appender.append(event(Level.INFO, "info index=" + index)));

            // When
            ReflectionTestUtils.invokeMethod(appender, "flushBatch", List.of(event(Level.ERROR, "flushed")));

            // Then
            final var requestCaptor = ArgumentCaptor.forClass(PutLogEventsRequest.class);
            verify(client).putLogEvents(requestCaptor.capture());
            assertThat(requestCaptor.getValue().logEvents()).anySatisfy(
                    logEvent -> assertThat(logEvent.message()).contains("cloudwatch appender dropped log events")
                            .contains("queueCapacity=2").contains("droppedLowPriority=").contains("saturationCount="));
        }
    }

    @Nested
    @DisplayName("종료 처리는")
    class Shutdown {

        @Test
        @DisplayName("남은 큐를 maxBatchSize 단위로 나눠 전송한다")
        void drainsRemainingQueueInBoundedBatches() {
            // Given
            final var client = mock(CloudWatchLogsClient.class);
            final var appender = startedAppender(client, 100);
            appender.setMaxBatchSize(10);
            IntStream.range(0, 25).forEach(index -> appender.append(event(Level.ERROR, "pending index=" + index)));

            // When
            appender.stop();

            // Then
            final var requestCaptor = ArgumentCaptor.forClass(PutLogEventsRequest.class);
            verify(client, times(3)).putLogEvents(requestCaptor.capture());
            assertThat(requestCaptor.getAllValues())
                    .allSatisfy(request -> assertThat(request.logEvents()).hasSizeLessThanOrEqualTo(10));
            assertThat(appender.getQueueSize()).isZero();
            assertThat(appender.getDiscardedOnShutdownCount()).isZero();
        }

        @Test
        @DisplayName("전송이 지연되면 제한 시간 안에 종료하고 남은 이벤트를 드롭한다")
        void stopsWithinTimeoutAndDropsRemainingEvents() {
            // Given
            final var appender = startedAppender(slowClient(50), 2_000);
            appender.setMaxBatchSize(10);
            appender.setShutdownTimeoutMillis(300);
            IntStream.range(0, 2_000).forEach(index -> appender.append(event(Level.ERROR, "pending index=" + index)));

            // When
            final var startedAt = System.currentTimeMillis();
            appender.stop();
            final var elapsed = System.currentTimeMillis() - startedAt;

            // Then
            assertThat(elapsed).isLessThan(5_000);
            assertThat(appender.getQueueSize()).isZero();
            assertThat(appender.getDiscardedOnShutdownCount()).isGreaterThan(1_000);
        }

        @Test
        @DisplayName("producer와 writer가 동시에 동작해도 큐 상한을 지키고 이벤트 수가 맞는다")
        void keepsCapacityUnderProducerWriterContention() throws Exception {
            // Given
            final var producerCount = 4;
            final var eventsPerProducer = 500;
            final var capacity = 50;
            final var release = new CountDownLatch(1);
            final var sentRequests = Collections.synchronizedList(new ArrayList<PutLogEventsRequest>());
            final var appender = startedAppender(stalledClient(release, sentRequests), capacity);
            appender.setMaxBatchSize(10);
            appender.setMaxBatchTimeMillis(50);
            appender.setMaxBlockTimeMillis(0);
            appender.setShutdownTimeoutMillis(5_000);
            final var writer = startWriter(appender);
            final var overflow = Collections.synchronizedList(new ArrayList<Integer>());

            // When
            final var startGate = new CountDownLatch(1);
            final var producers = IntStream.range(0, producerCount).mapToObj(producer -> new Thread(() -> {
                awaitQuietly(startGate);
                for (var index = 0; index < eventsPerProducer; index++) {
                    final var level = index % 2 == 0 ? Level.INFO : Level.ERROR;
                    appender.append(event(level, "contention producer=" + producer + " index=" + index));
                    if (appender.getQueueSize() > capacity) {
                        overflow.add(appender.getQueueSize());
                    }
                }
            })).toList();
            producers.forEach(Thread::start);
            startGate.countDown();
            for (final var producer : producers) {
                producer.join(10_000);
            }
            release.countDown();
            appender.stop();
            writer.join(10_000);

            // Then
            final var produced = producerCount * eventsPerProducer;
            final var sent = sentRequests.stream().flatMap(request -> request.logEvents().stream())
                    .filter(logEvent -> logEvent.message().contains("contention producer=")).count();
            final var dropped = appender.getDiscardedLowPriorityCount() + appender.getDiscardedHighPriorityCount()
                    + appender.getDiscardedOnShutdownCount();
            assertThat(overflow).isEmpty();
            assertThat(appender.getQueueSize()).isZero();
            assertThat(sent + dropped).isEqualTo(produced);
        }
    }

    private CloudWatchAppender appender(final CloudWatchLogsClient client) {
        final var appender = new CloudWatchAppender();
        appender.setContext(new LoggerContext());
        appender.setLogGroupName("test-group");
        appender.setMaxRetries(1);
        ReflectionTestUtils.setField(appender, "actualLogStreamName", "test-stream");
        ReflectionTestUtils.setField(appender, "cloudWatchClient", client);
        return appender;
    }

    /**
     * 실제 AWS 호출 없이 append 경로를 쓰기 위해 큐 용량만 설정하고 시작 상태로 표시한다.
     */
    private CloudWatchAppender startedAppender(final CloudWatchLogsClient client, final int queueCapacity) {
        final var appender = appender(client);
        appender.setQueueCapacity(queueCapacity);
        ReflectionTestUtils.setField(appender, "started", true);
        return appender;
    }

    private Thread startWriter(final CloudWatchAppender appender) {
        ReflectionTestUtils.setField(appender, "running", true);
        final var writer = new Thread(() -> ReflectionTestUtils.invokeMethod(appender, "runWriter"), "test-writer");
        writer.setDaemon(true);
        ReflectionTestUtils.setField(appender, "writerThread", writer);
        writer.start();
        return writer;
    }

    /**
     * CloudWatch 전송이 멈춘 상황을 만들기 위해 래치가 열릴 때까지 응답을 지연시킨다.
     *
     * <p>
     * 종료 시 writer가 interrupt되어도 전송이 예외로 실패하지 않도록 interrupt를 던지지 않는 방식으로 대기한다.
     */
    private CloudWatchLogsClient stalledClient(final CountDownLatch release,
            final List<PutLogEventsRequest> sentRequests) {
        final var client = mock(CloudWatchLogsClient.class);
        when(client.putLogEvents(any(PutLogEventsRequest.class))).thenAnswer(invocation -> {
            final var waitDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while (release.getCount() > 0 && System.nanoTime() < waitDeadline) {
                LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(1));
            }
            sentRequests.add(invocation.getArgument(0));
            return PutLogEventsResponse.builder().build();
        });
        return client;
    }

    /** 전송 1회당 고정 지연을 주어 종료 기한이 실제로 전송을 끊는지 확인한다. */
    private CloudWatchLogsClient slowClient(final long delayMillis) {
        final var client = mock(CloudWatchLogsClient.class);
        when(client.putLogEvents(any(PutLogEventsRequest.class))).thenAnswer(invocation -> {
            final var waitDeadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(delayMillis);
            while (System.nanoTime() < waitDeadline) {
                LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(1));
            }
            return PutLogEventsResponse.builder().build();
        });
        return client;
    }

    @SuppressWarnings("unchecked")
    private List<Level> levelsInQueue(final CloudWatchAppender appender) {
        final var queue = (BlockingQueue<ILoggingEvent>) ReflectionTestUtils.getField(appender, "logQueue");
        return queue.stream().map(ILoggingEvent::getLevel).toList();
    }

    private void awaitQuietly(final CountDownLatch latch) {
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private LoggingEvent event(final Level level, final String message) {
        return loggingEvent(level, message, null, Map.of());
    }

    private LoggingEvent loggingEvent(final String message, final Throwable throwable, final Map<String, String> mdc) {
        return loggingEvent(Level.ERROR, message, throwable, mdc);
    }

    private LoggingEvent loggingEvent(final Level level,
            final String message,
            final Throwable throwable,
            final Map<String, String> mdc) {
        final var event = new LoggingEvent();
        event.setLevel(level);
        event.setLoggerName("test.Logger");
        event.setThreadName("request-thread");
        event.setMessage(message);
        if (throwable != null) {
            event.setThrowableProxy(new ThrowableProxy(throwable));
        }
        event.setMDCPropertyMap(mdc);
        return event;
    }
}
