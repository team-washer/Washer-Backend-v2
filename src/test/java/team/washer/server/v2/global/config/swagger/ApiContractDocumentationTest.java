package team.washer.server.v2.global.config.swagger;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.Arrays;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import team.washer.server.v2.domain.reservation.controller.ReservationController;
import team.washer.server.v2.domain.reservation.dto.response.ActiveReservationApiResponseResDto;
import team.washer.server.v2.domain.reservation.dto.response.RoomActiveReservationsApiResponseResDto;
import team.washer.server.v2.global.common.error.dto.response.CommonErrorResponseResDto;

@DisplayName("API 계약 문서화 검증")
class ApiContractDocumentationTest {

    @Test
    @DisplayName("공통 오류 응답은 실제 wrapper 필드와 상태 코드를 문서화해야 한다")
    void commonErrorResponses_ShouldDocumentRuntimeWrapper() {
        final var responses = CommonErrorResponses.class.getAnnotation(ApiResponses.class);

        assertThat(responses).isNotNull();
        assertThat(Arrays.stream(responses.value()).map(ApiResponse::responseCode).toList())
                .contains("400", "401", "403", "404", "405", "409", "413", "415", "500", "503");
        assertThat(CommonErrorResponseResDto.class.getRecordComponents()).extracting(component -> component.getName())
                .containsExactly("status", "code", "message", "data");
    }

    @Test
    @DisplayName("내 활성 예약이 없으면 data가 null임을 문서화해야 한다")
    void activeReservation_ShouldDocumentNullableData() throws NoSuchMethodException {
        final var response = responseOf(ReservationController.class.getMethod("getActiveReservation"), "200");

        assertThat(response.description()).contains("data가 null");
        assertThat(response.content()[0].schema().implementation()).isEqualTo(ActiveReservationApiResponseResDto.class);
    }

    @Test
    @DisplayName("호실 활성 예약이 없으면 빈 배열임을 문서화해야 한다")
    void roomActiveReservations_ShouldDocumentEmptyArray() throws NoSuchMethodException {
        final var response = responseOf(ReservationController.class.getMethod("getRoomActiveReservations"), "200");

        assertThat(response.description()).contains("빈 배열([])");
        assertThat(response.content()[0].schema().implementation())
                .isEqualTo(RoomActiveReservationsApiResponseResDto.class);
    }

    @Test
    @DisplayName("예약 취소 중 사용 시작 충돌을 409로 문서화해야 한다")
    void cancelReservation_ShouldDocumentConflict() throws NoSuchMethodException {
        final Method method = ReservationController.class.getMethod("cancelReservation", Long.class);
        final var response = responseOf(method, "409");

        assertThat(response.description()).contains("취소할 수 없음");
    }

    private ApiResponse responseOf(final Method method, final String responseCode) {
        final var responses = method.getAnnotation(ApiResponses.class);
        assertThat(responses).isNotNull();
        return Arrays.stream(responses.value()).filter(response -> response.responseCode().equals(responseCode))
                .findFirst().orElseThrow();
    }
}
