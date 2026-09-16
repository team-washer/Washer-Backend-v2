package team.washer.server.v2.global.common.error.handler;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import team.washer.server.v2.global.common.error.ErrorCode;
import team.washer.server.v2.global.common.error.exception.ErrorCodeException;

@DisplayName("GlobalExceptionHandler 단위 테스트")
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler globalExceptionHandler = new GlobalExceptionHandler();

    @Nested
    @DisplayName("errorCodeException 메서드는")
    class Describe_errorCodeException {

        @Test
        @DisplayName("기존 status·code·message 구조를 유지하고 data.errorCode에 오류 코드를 담는다")
        void it_returns_error_code_in_data() {
            // Given
            var exception = new ErrorCodeException(ErrorCode.RESERVATION_RESTRICTION_UNAVAILABLE);

            // When
            var response = globalExceptionHandler.errorCodeException(exception);

            // Then
            assertThat(response.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
            assertThat(response.getCode()).isEqualTo(503);
            assertThat(response.getMessage()).isEqualTo("예약 제한 정보를 확인할 수 없습니다. 잠시 후 다시 시도해 주세요.");
            assertThat(response.getData()).isEqualTo(Map.of("errorCode", "RESERVATION_RESTRICTION_UNAVAILABLE"));
        }
    }
}
