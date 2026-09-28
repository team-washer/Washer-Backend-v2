package team.washer.server.v2.global.thirdparty.aws.cloudwatch.appender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxy;
import software.amazon.awssdk.services.cloudwatchlogs.CloudWatchLogsClient;
import software.amazon.awssdk.services.cloudwatchlogs.model.PutLogEventsRequest;
import team.washer.server.v2.global.common.trace.TraceIdFilter;

@DisplayName("CloudWatch Appender 로그 직렬화는")
class CloudWatchAppenderTest {

    @Test
    @DisplayName("예외 원인과 허용된 추적 문맥을 비동기 전송 payload에 보존한다")
    void preservesExceptionAndTraceContextInPayload() {
        final var client = mock(CloudWatchLogsClient.class);
        final var appender = new CloudWatchAppender();
        appender.setLogGroupName("test-group");
        appender.setMaxRetries(1);
        ReflectionTestUtils.setField(appender, "actualLogStreamName", "test-stream");
        ReflectionTestUtils.setField(appender, "cloudWatchClient", client);

        final var event = new LoggingEvent();
        event.setLevel(Level.ERROR);
        event.setLoggerName("test.Logger");
        event.setThreadName("request-thread");
        event.setMessage("request failed");
        event.setThrowableProxy(new ThrowableProxy(new IllegalStateException(
                "root cause Authorization: Basic dXNlcjpwYXNz, password=\"correct horse battery staple\", "
                        + "access_token=access-secret {\"refresh_token\":\"refresh-secret\"}")));
        event.setMDCPropertyMap(Map.of(TraceIdFilter.MDC_KEY, "trace-123", "Authorization", "secret-token"));

        ReflectionTestUtils.invokeMethod(appender, "flushBatch", List.of(event));

        final var requestCaptor = org.mockito.ArgumentCaptor.forClass(PutLogEventsRequest.class);
        verify(client).putLogEvents(requestCaptor.capture());
        final var message = requestCaptor.getValue().logEvents().getFirst().message();

        assertThat(message).contains("level=\"ERROR\"").contains("logger=\"test.Logger\"")
                .contains("traceId=\"trace-123\"").contains("exceptionType=\"java.lang.IllegalStateException\"")
                .contains("root cause").doesNotContain("dXNlcjpwYXNz").doesNotContain("correct horse battery staple")
                .doesNotContain("access-secret").doesNotContain("refresh-secret").contains("Authorization: [REDACTED]")
                .contains("password=\\\"[REDACTED]\\\"").contains("access_token=[REDACTED]")
                .contains("{\\\"refresh_token\\\":\\\"[REDACTED]\\\"}");
    }

    @Test
    @DisplayName("긴 예외 추적 정보는 설정된 길이에서 잘라낸다")
    void truncatesLongThrowable() {
        final var client = mock(CloudWatchLogsClient.class);
        final var appender = new CloudWatchAppender();
        appender.setLogGroupName("test-group");
        appender.setMaxRetries(1);
        appender.setMaxThrowableLength(40);
        ReflectionTestUtils.setField(appender, "actualLogStreamName", "test-stream");
        ReflectionTestUtils.setField(appender, "cloudWatchClient", client);
        final var event = new LoggingEvent();
        event.setLevel(Level.ERROR);
        event.setLoggerName("test.Logger");
        event.setMessage("failed");
        event.setThrowableProxy(new ThrowableProxy(new IllegalStateException("a very long root cause")));
        event.setMDCPropertyMap(Map.of());

        ReflectionTestUtils.invokeMethod(appender, "flushBatch", List.of(event));
        final var requestCaptor = org.mockito.ArgumentCaptor.forClass(PutLogEventsRequest.class);
        verify(client).putLogEvents(requestCaptor.capture());
        final var message = requestCaptor.getValue().logEvents().getFirst().message();

        assertThat(message).contains("...[truncated]");
    }
}
