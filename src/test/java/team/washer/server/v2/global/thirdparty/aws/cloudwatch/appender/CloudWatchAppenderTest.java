package team.washer.server.v2.global.thirdparty.aws.cloudwatch.appender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxy;
import software.amazon.awssdk.services.cloudwatchlogs.CloudWatchLogsClient;
import software.amazon.awssdk.services.cloudwatchlogs.model.PutLogEventsRequest;
import team.washer.server.v2.global.common.trace.TraceIdFilter;

@DisplayName("CloudWatch Appender 로그 직렬화는")
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
                    Map.of(TraceIdFilter.MDC_KEY, "trace-123"));

            // When
            ReflectionTestUtils.invokeMethod(appender, "flushBatch", List.of(event));

            // Then
            final var requestCaptor = ArgumentCaptor.forClass(PutLogEventsRequest.class);
            verify(client).putLogEvents(requestCaptor.capture());
            final var message = requestCaptor.getValue().logEvents().getFirst().message();
            assertThat(message).contains("level=\"ERROR\"").contains("logger=\"test.Logger\"")
                    .contains("traceId=\"trace-123\"").contains("exceptionType=\"java.lang.IllegalStateException\"")
                    .contains("root cause").doesNotContain("dXNlcjpwYXNz")
                    .doesNotContain("correct horse battery staple").doesNotContain("access-secret")
                    .doesNotContain("refresh-secret").contains("Authorization: [REDACTED]")
                    .contains("password=\\\"[REDACTED]\\\"").contains("access_token=[REDACTED]")
                    .contains("{\\\"refresh_token\\\":\\\"[REDACTED]\\\"}");
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

    private CloudWatchAppender appender(final CloudWatchLogsClient client) {
        final var appender = new CloudWatchAppender();
        appender.setLogGroupName("test-group");
        appender.setMaxRetries(1);
        ReflectionTestUtils.setField(appender, "actualLogStreamName", "test-stream");
        ReflectionTestUtils.setField(appender, "cloudWatchClient", client);
        return appender;
    }

    private LoggingEvent loggingEvent(final String message, final Throwable throwable, final Map<String, String> mdc) {
        final var event = new LoggingEvent();
        event.setLevel(Level.ERROR);
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
