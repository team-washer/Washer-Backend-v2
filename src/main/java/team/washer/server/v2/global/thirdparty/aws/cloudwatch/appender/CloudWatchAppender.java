package team.washer.server.v2.global.thirdparty.aws.cloudwatch.appender;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import ch.qos.logback.classic.spi.ILoggingEvent;
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
import team.washer.server.v2.global.common.trace.TraceIdFilter;

/**
 * AWS CloudWatch Logs로 로그를 비동기 배치 전송하는 Logback Appender.
 *
 * <p>
 * logback-spring.xml에서 설정하며, {@code stage} 프로파일에서만 활성화됩니다. AWS 자격증명은
 * {@code accessKey} / {@code secretKey} 프로퍼티로 주입받습니다.
 */
public class CloudWatchAppender extends UnsynchronizedAppenderBase<ILoggingEvent> {

    private static final Pattern BEARER_TOKEN_PATTERN = Pattern.compile("(?i)Bearer\\s+[^\\r\\n,;}\\]]+");
    private static final Pattern AUTHORIZATION_VALUE_PATTERN = Pattern
            .compile("(?i)([\\\"']?authorization[\\\"']?\\s*[:=]\\s*)(\\\"[^\\\"]*\\\"|'[^']*'|[^\\r\\n,;}\\]]+)");
    private static final Pattern SENSITIVE_VALUE_PATTERN = Pattern.compile(
            "(?i)([\\\"']?(?:access_token|refresh_token|smartthings_token|password|token)[\\\"']?)(\\s*[:=]\\s*)(\\\"[^\\\"]*\\\"|'[^']*'|[^\\s,;}\\]]+)");

    private String logGroupName;
    private String logStreamNamePrefix;
    private String region = Region.AP_NORTHEAST_2.id();
    private String accessKey;
    private String secretKey;
    private int maxBatchSize = 50;
    private long maxBatchTimeMillis = 10_000;
    private long maxBlockTimeMillis = 5_000;
    private int retentionTimeDays = 30;
    private long shutdownTimeoutMillis = 5_000;
    private int maxRetries = 3;
    private int maxMessageLength = 10_000;
    private int maxThrowableLength = 20_000;

    private CloudWatchLogsClient cloudWatchClient;
    private final BlockingQueue<ILoggingEvent> logQueue = new LinkedBlockingQueue<>();
    private Thread writerThread;
    private String actualLogStreamName;

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
            addInfo("CloudWatchAppender started logGroup=" + logGroupName + " stream=" + actualLogStreamName);
        } catch (Exception e) {
            addError("Failed to start CloudWatchAppender", e);
        }
    }

    @Override
    public void stop() {
        running = false;
        if (writerThread != null) {
            writerThread.interrupt();
            try {
                writerThread.join(shutdownTimeoutMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        try {
            flushLogs();
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
        addInfo("CloudWatchAppender stopped");
    }

    @Override
    protected void append(final ILoggingEvent eventObject) {
        if (!isStarted()) {
            return;
        }
        // 전송 스레드에서 읽기 전에 요청 스레드의 MDC(추적 ID)와 메시지를 확정해 둔다
        eventObject.prepareForDeferredProcessing();
        try {
            var success = logQueue.offer(eventObject, maxBlockTimeMillis, TimeUnit.MILLISECONDS);
            if (!success) {
                addWarn("Log queue is full, dropping log event");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            addError("Interrupted while adding log event to queue", e);
        }
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
        var batch = new ArrayList<ILoggingEvent>(maxBatchSize);
        var lastFlushTime = System.currentTimeMillis();

        while (running || !logQueue.isEmpty()) {
            try {
                var event = logQueue.poll(1_000, TimeUnit.MILLISECONDS);
                if (event != null) {
                    batch.add(event);
                }

                var now = System.currentTimeMillis();
                var shouldFlush = batch.size() >= maxBatchSize
                        || (!batch.isEmpty() && (now - lastFlushTime) >= maxBatchTimeMillis);

                if (shouldFlush) {
                    flushBatch(batch);
                    batch.clear();
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

    private void flushLogs() {
        var batch = new ArrayList<ILoggingEvent>();
        logQueue.drainTo(batch);
        if (!batch.isEmpty()) {
            flushBatch(batch);
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
        if (traceId == null) {
            appendField(builder, "message", event.getFormattedMessage(), maxMessageLength);
        } else {
            appendField(builder, TraceIdFilter.MDC_KEY, traceId);
            appendField(builder, "message", event.getFormattedMessage(), maxMessageLength);
        }

        if (event.getThrowableProxy() != null) {
            appendField(builder, "exceptionType", event.getThrowableProxy().getClassName());
            appendField(builder,
                    "exception",
                    ThrowableProxyUtil.asString(event.getThrowableProxy()),
                    maxThrowableLength);
        }
        return builder.toString();
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
        final var sanitized = sanitize(value);
        final var normalized = sanitized.replace("\\", "\\\\").replace("\r", "\\r").replace("\n", "\\n").replace("\"",
                "\\\"");
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, Math.max(0, maxLength - 15)) + "...[truncated]";
    }

    private String sanitize(final String value) {
        var sanitized = replaceSensitiveValues(value, AUTHORIZATION_VALUE_PATTERN);
        sanitized = BEARER_TOKEN_PATTERN.matcher(sanitized).replaceAll("Bearer [REDACTED]");
        return replaceSensitiveValues(sanitized, SENSITIVE_VALUE_PATTERN);
    }

    private String replaceSensitiveValues(final String value, final Pattern pattern) {
        final var matcher = pattern.matcher(value);
        final var result = new StringBuffer();
        while (matcher.find()) {
            final var prefix = new StringBuilder();
            for (var group = 1; group < matcher.groupCount(); group++) {
                prefix.append(matcher.group(group));
            }
            final var matchedValue = matcher.group(matcher.groupCount());
            final var replacement = isQuoted(matchedValue)
                    ? prefix + matchedValue.substring(0, 1) + "[REDACTED]"
                            + matchedValue.substring(matchedValue.length() - 1)
                    : prefix + "[REDACTED]";
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private boolean isQuoted(final String value) {
        if (value.length() < 2) {
            return false;
        }
        final var first = value.charAt(0);
        final var last = value.charAt(value.length() - 1);
        return (first == '"' && last == '"') || (first == '\'' && last == '\'');
    }

    private void flushBatch(final List<ILoggingEvent> batch) {
        if (batch.isEmpty()) {
            return;
        }

        var logEvents = batch.stream().map(
                event -> InputLogEvent.builder().timestamp(event.getTimeStamp()).message(formatMessage(event)).build())
                .sorted((a, b) -> Long.compare(a.timestamp(), b.timestamp())).toList();

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
                    addError("Max retries exceeded, dropping batch size=" + batch.size());
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
}
