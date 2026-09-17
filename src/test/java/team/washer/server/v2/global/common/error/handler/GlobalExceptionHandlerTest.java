package team.washer.server.v2.global.common.error.handler;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;

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
    }
}
