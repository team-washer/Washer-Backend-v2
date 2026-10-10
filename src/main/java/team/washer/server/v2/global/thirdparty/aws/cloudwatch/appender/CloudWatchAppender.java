package team.washer.server.v2.global.thirdparty.aws.cloudwatch.appender;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.UnsynchronizedAppenderBase;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cloudwatchlogs.CloudWatchLogsClient;
import software.amazon.awssdk.services.cloudwatchlogs.model.CreateLogGroupRequest;
import software.amazon.awssdk.services.cloudwatchlogs.model.CreateLogStreamRequest;
import software.amazon.awssdk.services.cloudwatchlogs.model.InputLogEvent;
import software.amazon.awssdk.services.cloudwatchlogs.model.PutLogEventsRequest;
import software.amazon.awssdk.services.cloudwatchlogs.model.PutRetentionPolicyRequest;
import software.amazon.awssdk.services.cloudwatchlogs.model.ResourceAlreadyExistsException;
import software.amazon.awssdk.services.cloudwatchlogs.model.ResourceNotFoundException;
import team.washer.server.v2.domain.reservation.service.impl.ProcessReservationLifecycleServiceImpl;
import team.washer.server.v2.global.common.logging.SensitiveLogSanitizer;
import team.washer.server.v2.global.common.trace.TraceIdFilter;

/**
 * AWS CloudWatch Logs로 로그를 비동기 배치 전송하는 Logback Appender.
 *
 * <p>
 * logback-spring.xml에서 설정하며, {@code stage} 프로파일에서만 활성화됩니다. AWS 자격증명은
 * {@code accessKey} / {@code secretKey} 프로퍼티로 주입받습니다.
 *
 * <p>
 * 전송 지연이 길어져도 힙 사용량이 무제한으로 늘지 않도록 큐는 {@code queueCapacity}로 제한됩니다. 남은 용량이
 * {@code discardThresholdPercent} 이하로 떨어지면 INFO 이하 로그를 먼저 버려 WARN/ERROR 자리를
 * 확보하고, 큐가 완전히 차면 WARN/ERROR는 낮은 중요도 이벤트를 밀어내고 적재를 재시도합니다. 드롭·포화 수치는 다음 배치에 요약
 * 이벤트로 함께 전송되어 CloudWatch에서 바로 관찰할 수 있습니다.
 */
public class CloudWatchAppender extends UnsynchronizedAppenderBase<ILoggingEvent> {

    private static final int CLOUDWATCH_MAX_BATCH_BYTES = 1_048_576;
    private static final int CLOUDWATCH_EVENT_OVERHEAD_BYTES = 26;
    private static final int DEFAULT_QUEUE_CAPACITY = 10_000;
    private static final int DEFAULT_DISCARD_THRESHOLD_PERCENT = 20;
    /** INFO 이하(INFO/DEBUG/TRACE)는 포화 시 먼저 버릴 수 있는 낮은 중요도 로그로 취급한다. */
    private static final int DISCARDABLE_MAX_LEVEL_INT = Level.INFO.toInt();
    /** 포화 시 밀어낼 낮은 중요도 이벤트를 찾는 탐색 범위. 요청 스레드가 큐 전체를 훑지 않도록 제한한다. */
    private static final int EVICTION_SCAN_LIMIT = 1_024;
    private static final long SATURATION_WARN_INTERVAL_MILLIS = 60_000;

    private String logGroupName;
    private String logStreamNamePrefix;
    private String region = Region.AP_NORTHEAST_2.id();
    private String accessKey;
    private String secretKey;
    private int maxBatchSize = 50;
    private long maxBatchTimeMillis = 10_000;
    private long maxBlockTimeMillis = 1_000;
    private int retentionTimeDays = 30;
    private long shutdownTimeoutMillis = 5_000;
    private int maxRetries = 3;
    private int maxMessageLength = 10_000;
    private int maxThrowableLength = 20_000;
    private int queueCapacity = DEFAULT_QUEUE_CAPACITY;
    private int discardThresholdPercent = DEFAULT_DISCARD_THRESHOLD_PERCENT;

    private CloudWatchLogsClient cloudWatchClient;
    private volatile BlockingQueue<ILoggingEvent> logQueue = new LinkedBlockingQueue<>(DEFAULT_QUEUE_CAPACITY);
    private volatile int discardThreshold = DEFAULT_QUEUE_CAPACITY * DEFAULT_DISCARD_THRESHOLD_PERCENT / 100;
    private Thread writerThread;
    private String actualLogStreamName;

    private final AtomicLong discardedLowPriorityCount = new AtomicLong();
    private final AtomicLong discardedHighPriorityCount = new AtomicLong();
    private final AtomicLong evictedLowPriorityCount = new AtomicLong();
    private final AtomicLong discardedOnShutdownCount = new AtomicLong();
    private final AtomicLong saturationCount = new AtomicLong();
    private final AtomicLong reportedDropCount = new AtomicLong();
    private final AtomicLong lastSaturationWarnMillis = new AtomicLong();

    @SuppressWarnings("FieldMayBeFinal")
    private volatile boolean running = false;

    @Override
    public void start() {
        if (logGroupName == null || logGroupName.isBlank()) {
            addError("logGroupName must be set");
            return;
        }
        if (logStreamNamePrefix == null || logStreamNamePrefix.isBlank()) {
            addError("logStreamNamePrefix must be set");
            return;
        }
        if (accessKey == null || accessKey.isBlank()) {
            addError("accessKey must be set");
            return;
        }
        if (secretKey == null || secretKey.isBlank()) {
            addError("secretKey must be set");
            return;
        }

        actualLogStreamName = logStreamNamePrefix + UUID.randomUUID();
        initializeQueue();

        try {
            cloudWatchClient = CloudWatchLogsClient.builder().region(Region.of(region)).credentialsProvider(
                    StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey))).build();

            initializeLogGroup();
            initializeLogStream();

            running = true;
            writerThread = new Thread(this::runWriter, "CloudWatchAppender-Writer-" + name);
            writerThread.setDaemon(true);
            writerThread.start();

            super.start();
            addInfo("CloudWatchAppender started logGroup=" + logGroupName + " stream=" + actualLogStreamName
                    + " queueCapacity=" + queueCapacity + " discardThreshold=" + discardThreshold);
        } catch (Exception e) {
            addError("Failed to start CloudWatchAppender", e);
        }
    }

    @Override
    public void stop() {
        running = false;
        // writer 종료 대기와 잔여 flush가 각각 타임아웃을 쓰지 않도록 종료 전체에 하나의 기한을 적용한다
        final var shutdownDeadline = System.currentTimeMillis() + Math.max(0, shutdownTimeoutMillis);
        if (writerThread != null) {
            writerThread.interrupt();
            try {
                writerThread.join(Math.max(1, remainingMillis(shutdownDeadline)));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        try {
            flushRemaining(shutdownDeadline);
        } catch (Exception e) {
            addError("Error flushing logs during shutdown", e);
        }

        if (cloudWatchClient != null) {
            try {
                cloudWatchClient.close();
            } catch (Exception e) {
                addError("Error closing CloudWatch client", e);
            }
        }

        super.stop();
        addInfo("CloudWatchAppender stopped " + dropStatistics());
    }

    @Override
    protected void append(final ILoggingEvent eventObject) {
        if (!isStarted()) {
            return;
        }
        // 전송 스레드에서 읽기 전에 요청 스레드의 MDC(추적 ID)와 메시지를 확정해 둔다
        eventObject.prepareForDeferredProcessing();

        final var queue = logQueue;
        final var discardable = isDiscardable(eventObject);

        // 남은 용량이 임계치 이하면 WARN/ERROR 자리를 남겨 두기 위해 낮은 중요도 로그부터 버린다
        if (discardable && queue.remainingCapacity() <= discardThreshold) {
            discardedLowPriorityCount.incrementAndGet();
            warnSaturation("Log queue near capacity, dropping low priority log event");
            return;
        }
        if (queue.offer(eventObject)) {
            return;
        }

        saturationCount.incrementAndGet();
        if (discardable) {
            discardedLowPriorityCount.incrementAndGet();
            warnSaturation("Log queue is full, dropping low priority log event");
            return;
        }

        // WARN/ERROR는 낮은 중요도 이벤트를 밀어내고 적재를 재시도한다
        if (evictDiscardableEvent(queue) && queue.offer(eventObject)) {
            return;
        }
        if (maxBlockTimeMillis > 0) {
            try {
                if (queue.offer(eventObject, maxBlockTimeMillis, TimeUnit.MILLISECONDS)) {
                    return;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                discardedHighPriorityCount.incrementAndGet();
                addError("Interrupted while adding log event to queue", e);
                return;
            }
        }
        discardedHighPriorityCount.incrementAndGet();
        warnSaturation("Log queue is full, dropping high priority log event");
    }

    private void initializeQueue() {
        final var capacity = Math.max(1, queueCapacity);
        final var threshold = Math.clamp(discardThresholdPercent, 0, 100);
        logQueue = new LinkedBlockingQueue<>(capacity);
        discardThreshold = capacity * threshold / 100;
    }

    private boolean isDiscardable(final ILoggingEvent event) {
        return event.getLevel().toInt() <= DISCARDABLE_MAX_LEVEL_INT;
    }

    /**
     * 큐 앞쪽에서 낮은 중요도 이벤트 한 건을 제거해 WARN/ERROR가 들어갈 자리를 만든다.
     *
     * <p>
     * writer가 먼저 꺼내 간 이벤트를 드롭으로 집계하지 않도록, 실제 제거에 성공한 경우만 센다.
     *
     * @return 자리를 확보했으면 {@code true}
     */
    private boolean evictDiscardableEvent(final BlockingQueue<ILoggingEvent> queue) {
        var scanned = 0;
        for (final var candidate : queue) {
            if (scanned++ >= EVICTION_SCAN_LIMIT) {
                return false;
            }
            if (isDiscardable(candidate) && queue.remove(candidate)) {
                evictedLowPriorityCount.incrementAndGet();
                discardedLowPriorityCount.incrementAndGet();
                return true;
            }
        }
        return false;
    }

    /** 포화 경고가 상태 로그를 뒤덮지 않도록 일정 간격으로만 남긴다. */
    private void warnSaturation(final String message) {
        final var now = System.currentTimeMillis();
        final var last = lastSaturationWarnMillis.get();
        if (now - last >= SATURATION_WARN_INTERVAL_MILLIS && lastSaturationWarnMillis.compareAndSet(last, now)) {
            addWarn(message + " " + dropStatistics());
        }
    }

    private long remainingMillis(final long deadlineMillis) {
        return Math.max(0, deadlineMillis - System.currentTimeMillis());
    }

    private void initializeLogGroup() {
        try {
            cloudWatchClient.createLogGroup(CreateLogGroupRequest.builder().logGroupName(logGroupName).build());
            addInfo("Created log group=" + logGroupName);
        } catch (ResourceAlreadyExistsException ignored) {
            addInfo("Log group already exists logGroup=" + logGroupName);
        } catch (Exception e) {
            addError("Failed to create log group=" + logGroupName, e);
            throw e;
        }

        if (retentionTimeDays > 0) {
            try {
                cloudWatchClient.putRetentionPolicy(PutRetentionPolicyRequest.builder().logGroupName(logGroupName)
                        .retentionInDays(retentionTimeDays).build());
                addInfo("Set retention policy days=" + retentionTimeDays + " logGroup=" + logGroupName);
            } catch (Exception e) {
                addError("Failed to set retention policy logGroup=" + logGroupName, e);
            }
        }
    }

    private void initializeLogStream() {
        try {
            cloudWatchClient.createLogStream(CreateLogStreamRequest.builder().logGroupName(logGroupName)
                    .logStreamName(actualLogStreamName).build());
            addInfo("Created log stream=" + actualLogStreamName);
        } catch (ResourceAlreadyExistsException ignored) {
            addInfo("Log stream already exists stream=" + actualLogStreamName);
        } catch (Exception e) {
            addError("Failed to create log stream=" + actualLogStreamName, e);
            throw e;
        }
    }

    private void runWriter() {
        var batch = new ArrayList<ILoggingEvent>(Math.max(1, maxBatchSize));
        var lastFlushTime = System.currentTimeMillis();

        while (running || !logQueue.isEmpty()) {
            try {
                var event = logQueue.poll(1_000, TimeUnit.MILLISECONDS);
                if (event != null) {
                    batch.add(event);
                }

                var now = System.currentTimeMillis();
                var batchTimedOut = (now - lastFlushTime) >= maxBatchTimeMillis;
                var shouldFlush = batch.size() >= maxBatchSize || (!batch.isEmpty() && batchTimedOut);

                if (shouldFlush) {
                    flushBatch(batch);
                    batch.clear();
                    lastFlushTime = now;
                } else if (batch.isEmpty() && batchTimedOut && hasUnreportedDrops()) {
                    // 로그 생산이 멈춘 뒤에도 드롭 요약은 전송되어야 한다
                    flushDropSummary();
                    lastFlushTime = now;
                }
            } catch (InterruptedException ignored) {
                addInfo("Writer thread interrupted, flushing remaining logs");
                if (!running) {
                    break;
                }
            } catch (Exception e) {
                addError("Error in writer thread", e);
            }
        }

        if (!batch.isEmpty()) {
            try {
                flushBatch(batch);
            } catch (Exception e) {
                addError("Error flushing final batch", e);
            }
        }
    }

    /**
     * 종료 시 남은 큐를 배치 단위로 나눠 전송한다.
     *
     * <p>
     * 큐 전체를 한 목록으로 꺼내지 않고 {@code maxBatchSize}씩 꺼내며, 기한을 넘기면 남은 이벤트를 드롭해 외부 전송 지연
     * 때문에 종료가 늦어지지 않도록 한다.
     *
     * @param deadlineMillis
     *            종료 처리를 마쳐야 하는 시각(epoch millis)
     */
    private void flushRemaining(final long deadlineMillis) {
        final var batchSize = Math.max(1, maxBatchSize);
        final var batch = new ArrayList<ILoggingEvent>(batchSize);

        while (!logQueue.isEmpty()) {
            if (remainingMillis(deadlineMillis) == 0) {
                var abandoned = 0;
                while (logQueue.poll() != null) {
                    abandoned++;
                }
                discardedOnShutdownCount.addAndGet(abandoned);
                addWarn("Shutdown flush deadline exceeded, dropping remaining log events count=" + abandoned);
                return;
            }
            batch.clear();
            logQueue.drainTo(batch, batchSize);
            if (batch.isEmpty()) {
                return;
            }
            flushBatch(batch);
        }
    }

    private boolean hasUnreportedDrops() {
        return totalDroppedCount() > reportedDropCount.get();
    }

    private long totalDroppedCount() {
        return discardedLowPriorityCount.get() + discardedHighPriorityCount.get() + discardedOnShutdownCount.get();
    }

    private String dropStatistics() {
        return "queueCapacity=" + queueCapacity + " queueSize=" + logQueue.size() + " droppedLowPriority="
                + discardedLowPriorityCount.get() + " droppedHighPriority=" + discardedHighPriorityCount.get()
                + " evictedLowPriority=" + evictedLowPriorityCount.get() + " droppedOnShutdown="
                + discardedOnShutdownCount.get() + " saturationCount=" + saturationCount.get();
    }

    /**
     * 드롭·포화 수치를 CloudWatch에서도 볼 수 있도록 요약 이벤트로 만든다.
     *
     * @return 새로 보고할 드롭이 없으면 {@code null}
     */
    private InputLogEvent createDropSummaryEvent() {
        final var total = totalDroppedCount();
        if (total <= reportedDropCount.get()) {
            return null;
        }
        reportedDropCount.set(total);

        final var builder = new StringBuilder();
        appendField(builder, "level", Level.WARN.levelStr);
        appendField(builder, "logger", CloudWatchAppender.class.getName());
        appendField(builder, "thread", Thread.currentThread().getName());
        appendField(builder, "message", "cloudwatch appender dropped log events " + dropStatistics());
        return InputLogEvent.builder().timestamp(System.currentTimeMillis()).message(builder.toString()).build();
    }

    private void flushDropSummary() {
        final var summary = createDropSummaryEvent();
        if (summary != null) {
            sendBatch(List.of(summary));
        }
    }

    /**
     * 오류 응답의 추적 ID로 CloudWatch 로그를 검색할 수 있도록 요청 로그에 추적 ID를 붙인다.
     */
    private String formatMessage(final ILoggingEvent event) {
        final var builder = new StringBuilder();
        appendField(builder, "level", event.getLevel().levelStr);
        appendField(builder, "logger", event.getLoggerName());
        appendField(builder, "thread", event.getThreadName());

        final var mdcPropertyMap = event.getMDCPropertyMap();
        final var traceId = mdcPropertyMap == null ? null : mdcPropertyMap.get(TraceIdFilter.MDC_KEY);
        final var method = mdcPropertyMap == null ? null : mdcPropertyMap.get(TraceIdFilter.HTTP_METHOD_MDC_KEY);
        final var path = mdcPropertyMap == null ? null : mdcPropertyMap.get(TraceIdFilter.REQUEST_PATH_MDC_KEY);
        final var lifecycleRunId = mdcPropertyMap == null
                ? null
                : mdcPropertyMap.get(ProcessReservationLifecycleServiceImpl.LIFECYCLE_RUN_ID_MDC_KEY);
        appendField(builder, TraceIdFilter.MDC_KEY, traceId);
        appendField(builder, TraceIdFilter.HTTP_METHOD_MDC_KEY, method);
        appendField(builder, TraceIdFilter.REQUEST_PATH_MDC_KEY, path);
        appendField(builder, ProcessReservationLifecycleServiceImpl.LIFECYCLE_RUN_ID_MDC_KEY, lifecycleRunId);
        appendField(builder, "message", event.getFormattedMessage(), maxMessageLength);

        if (event.getThrowableProxy() != null) {
            appendField(builder, "exceptionType", event.getThrowableProxy().getClassName());
            appendField(builder, "exception", formatThrowable(event.getThrowableProxy()), maxThrowableLength);
        }
        return builder.toString();
    }

    private String formatThrowable(final IThrowableProxy throwable) {
        final var causes = new ArrayList<String>();
        var current = throwable;
        while (current != null) {
            causes.add(current.getClassName() + ": " + current.getMessage());
            current = current.getCause();
        }
        final var causeChain = new StringBuilder();
        for (var index = causes.size() - 1; index >= 0; index--) {
            if (causeChain.length() > 0) {
                causeChain.append(" <- ");
            }
            causeChain.append(causes.get(index));
        }
        return "causeChain=" + causeChain + "\n" + ThrowableProxyUtil.asString(throwable);
    }

    private void appendField(final StringBuilder builder, final String name, final String value) {
        appendField(builder, name, value, Integer.MAX_VALUE);
    }

    private void appendField(final StringBuilder builder, final String name, final String value, final int maxLength) {
        if (value == null) {
            return;
        }
        if (builder.length() > 0) {
            builder.append(' ');
        }
        builder.append(name).append("=\"").append(escapeAndTruncate(value, maxLength)).append('"');
    }

    private String escapeAndTruncate(final String value, final int maxLength) {
        final var sanitized = SensitiveLogSanitizer.sanitize(value);
        final var normalized = sanitized.replace("\\", "\\\\").replace("\r", "\\r").replace("\n", "\\n").replace("\"",
                "\\\"");
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, Math.max(0, maxLength - 15)) + "...[truncated]";
    }

    private void flushBatch(final List<ILoggingEvent> batch) {
        if (batch.isEmpty()) {
            return;
        }

        final var logEvents = new ArrayList<InputLogEvent>(batch.size() + 1);
        for (final var event : batch) {
            logEvents
                    .add(InputLogEvent.builder().timestamp(event.getTimeStamp()).message(formatMessage(event)).build());
        }
        final var dropSummary = createDropSummaryEvent();
        if (dropSummary != null) {
            logEvents.add(dropSummary);
        }
        logEvents.sort(Comparator.comparingLong(InputLogEvent::timestamp));

        for (final var logBatch : splitByByteLimit(logEvents)) {
            sendBatch(logBatch);
        }
    }

    private List<List<InputLogEvent>> splitByByteLimit(final List<InputLogEvent> logEvents) {
        final var batches = new ArrayList<List<InputLogEvent>>();
        var currentBatch = new ArrayList<InputLogEvent>();
        var currentBytes = 0;
        for (final var event : logEvents) {
            final var eventBytes = event.message().getBytes(StandardCharsets.UTF_8).length
                    + CLOUDWATCH_EVENT_OVERHEAD_BYTES;
            if (!currentBatch.isEmpty() && (currentBytes + eventBytes > CLOUDWATCH_MAX_BATCH_BYTES
                    || currentBatch.size() >= maxBatchSize)) {
                batches.add(currentBatch);
                currentBatch = new ArrayList<>();
                currentBytes = 0;
            }
            currentBatch.add(event);
            currentBytes += eventBytes;
        }
        if (!currentBatch.isEmpty()) {
            batches.add(currentBatch);
        }
        return batches;
    }

    private void sendBatch(final List<InputLogEvent> logEvents) {
        for (int attempt = 0; attempt < maxRetries; attempt++) {
            try {
                cloudWatchClient.putLogEvents(PutLogEventsRequest.builder().logGroupName(logGroupName)
                        .logStreamName(actualLogStreamName).logEvents(logEvents).build());
                return;
            } catch (ResourceNotFoundException e) {
                addError("Log group or stream not found, attempting to recreate attempt=" + (attempt + 1), e);
                try {
                    initializeLogGroup();
                    initializeLogStream();
                } catch (Exception recreateEx) {
                    addError("Failed to recreate log group/stream", recreateEx);
                }
                if (attempt >= maxRetries - 1) {
                    addError("Max retries exceeded, dropping batch size=" + logEvents.size());
                }
            } catch (Exception e) {
                addError("Failed to send log events to CloudWatch attempt=" + (attempt + 1), e);
                return;
            }
        }
    }

    // ---- Logback XML 프로퍼티 주입용 setter ----

    public void setLogGroupName(final String logGroupName) {
        this.logGroupName = logGroupName;
    }

    public void setLogStreamNamePrefix(final String logStreamNamePrefix) {
        this.logStreamNamePrefix = logStreamNamePrefix;
    }

    public void setRegion(final String region) {
        this.region = region;
    }

    public void setAccessKey(final String accessKey) {
        this.accessKey = accessKey;
    }

    public void setSecretKey(final String secretKey) {
        this.secretKey = secretKey;
    }

    public void setMaxBatchSize(final int maxBatchSize) {
        this.maxBatchSize = maxBatchSize;
    }

    public void setMaxBatchTimeMillis(final long maxBatchTimeMillis) {
        this.maxBatchTimeMillis = maxBatchTimeMillis;
    }

    /**
     * 큐가 완전히 찼을 때 WARN/ERROR 적재를 기다릴 최대 시간. {@code 0}이면 전혀 기다리지 않는다.
     *
     * <p>
     * 낮은 중요도 로그는 이 값과 무관하게 즉시 드롭되므로 업무 요청 스레드가 로그 때문에 길게 멈추지 않는다.
     */
    public void setMaxBlockTimeMillis(final long maxBlockTimeMillis) {
        this.maxBlockTimeMillis = maxBlockTimeMillis;
    }

    public void setRetentionTimeDays(final int retentionTimeDays) {
        this.retentionTimeDays = retentionTimeDays;
    }

    public void setShutdownTimeoutMillis(final long shutdownTimeoutMillis) {
        this.shutdownTimeoutMillis = shutdownTimeoutMillis;
    }

    public void setMaxRetries(final int maxRetries) {
        this.maxRetries = maxRetries;
    }

    public void setMaxMessageLength(final int maxMessageLength) {
        this.maxMessageLength = maxMessageLength;
    }

    public void setMaxThrowableLength(final int maxThrowableLength) {
        this.maxThrowableLength = maxThrowableLength;
    }

    /**
     * 큐에 보관할 최대 이벤트 수. 전송이 지연되어도 이 수를 넘겨 쌓이지 않는다.
     */
    public void setQueueCapacity(final int queueCapacity) {
        this.queueCapacity = queueCapacity;
        if (!isStarted()) {
            initializeQueue();
        }
    }

    /**
     * 낮은 중요도 로그를 버리기 시작하는 남은 용량 비율(%). {@code 20}이면 남은 용량이 20% 이하일 때 INFO 이하를 드롭한다.
     */
    public void setDiscardThresholdPercent(final int discardThresholdPercent) {
        this.discardThresholdPercent = discardThresholdPercent;
        if (!isStarted()) {
            initializeQueue();
        }
    }

    // ---- 포화·드롭 관찰용 조회 메서드 ----

    public int getQueueCapacity() {
        return queueCapacity;
    }

    public int getQueueSize() {
        return logQueue.size();
    }

    public long getDiscardedLowPriorityCount() {
        return discardedLowPriorityCount.get();
    }

    public long getDiscardedHighPriorityCount() {
        return discardedHighPriorityCount.get();
    }

    public long getEvictedLowPriorityCount() {
        return evictedLowPriorityCount.get();
    }

    public long getDiscardedOnShutdownCount() {
        return discardedOnShutdownCount.get();
    }

    public long getSaturationCount() {
        return saturationCount.get();
    }
}
