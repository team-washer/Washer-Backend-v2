package team.washer.server.v2.global.common.error.handler;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import team.themoment.sdk.exception.ExpectedException;
import team.washer.server.v2.global.common.error.code.ErrorCode;
import team.washer.server.v2.global.common.error.exception.ErrorCodeException;

@DisplayName("GlobalExceptionHandler 단위 테스트")
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler globalExceptionHandler = new GlobalExceptionHandler();

    @Nested
    @DisplayName("errorCodeException 메서드는")
    class Describe_errorCodeException {

        @Test
        @DisplayName("HTTP 상태를 503으로 지정하고 기존 message 구조를 유지하며 data.errorCode에 오류 코드를 담는다")
        void it_returns_error_code_in_data() {
            // Given
            var exception = new ErrorCodeException(ErrorCode.RESERVATION_RESTRICTION_UNAVAILABLE);
            var request = new MockHttpServletRequest("POST", "/api/v2/reservations");

            // When
            var response = globalExceptionHandler.errorCodeException(exception, request);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
            var body = response.getBody();
            assertThat(body).isNotNull();
            assertThat(body.getCode()).isEqualTo(503);
            assertThat(body.getMessage()).isEqualTo("예약 제한 정보를 확인할 수 없습니다. 잠시 후 다시 시도해 주세요.");
            assertThat(body.getData().errorCode()).isEqualTo("RESERVATION_RESTRICTION_UNAVAILABLE");
        }

        @Test
        @DisplayName("도메인 오류의 기존 message를 유지하면서 원인별 오류 코드를 응답한다")
        void it_preserves_domain_message_with_specific_error_code() {
            // Given
            var exception = new ErrorCodeException(ErrorCode.RESERVATION_COOLDOWN_ACTIVE, "예약 취소 후 5분간 세탁기 예약이 제한됩니다");
            var request = new MockHttpServletRequest("POST", "/api/v2/reservations");

            // When
            var response = globalExceptionHandler.errorCodeException(exception, request);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getMessage()).isEqualTo("예약 취소 후 5분간 세탁기 예약이 제한됩니다");
            assertThat(response.getBody().getData().errorCode()).isEqualTo("RESERVATION_COOLDOWN_ACTIVE");
        }
    }

    @Nested
    @DisplayName("예외 운영 로그는")
    class Describe_operational_logging {

        @Test
        @DisplayName("비예상 예외에 요청 진단 정보와 원인 stack trace를 남긴다")
        void logsUnexpectedExceptionWithDiagnostics() {
            // Given
            var logger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
            var appender = new ListAppender<ILoggingEvent>();
            appender.start();
            logger.addAppender(appender);
            var request = new MockHttpServletRequest("POST", "/api/v2/reservations");
            MDC.put("traceId", "trace-unexpected");

            try {
                // When
                globalExceptionHandler.unexpectedException(new IllegalStateException("root cause"), request);

                // Then
                var event = appender.list.stream().filter(item -> item.getLevel().toString().equals("ERROR"))
                        .findFirst().orElseThrow();
                assertThat(event.getFormattedMessage()).contains("status=500");
                assertThat(event.getMDCPropertyMap()).containsEntry("traceId", "trace-unexpected");
                assertThat(event.getThrowableProxy()).isNotNull();
                assertThat(event.getThrowableProxy().getClassName()).isEqualTo(IllegalStateException.class.getName());
                assertThat(event.getThrowableProxy().getMessage()).isEqualTo("root cause");
            } finally {
                MDC.remove("traceId");
                logger.detachAppender(appender);
            }
        }

        @Test
        @DisplayName("예상 가능한 예외에는 전체 stack trace를 중복 기록하지 않는다")
        void logsExpectedExceptionWithoutStackTrace() {
            // Given
            var logger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
            var appender = new ListAppender<ILoggingEvent>();
            appender.start();
            logger.addAppender(appender);
            var request = new MockHttpServletRequest("POST", "/api/v2/reservations");
            MDC.put("traceId", "trace-expected");

            try {
                // When
                globalExceptionHandler
                        .expectedException(new ExpectedException("invalid reservation", HttpStatus.CONFLICT), request);

                // Then
                var event = appender.list.stream().filter(item -> item.getLevel().toString().equals("WARN")).findFirst()
                        .orElseThrow();
                assertThat(event.getFormattedMessage()).contains("status=409");
                assertThat(event.getMDCPropertyMap()).containsEntry("traceId", "trace-expected");
                assertThat(event.getThrowableProxy()).isNull();
            } finally {
                MDC.remove("traceId");
                logger.detachAppender(appender);
            }
        }
    }
}
