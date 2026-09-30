package team.washer.server.v2.global.thirdparty.smartthings.feign;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import feign.Request;
import feign.Response;
import team.washer.server.v2.global.common.error.code.ErrorCode;

@DisplayName("SmartThings Feign 오류 계약")
class SmartThingsFeignErrorDecoderTest {

    private final SmartThingsFeignErrorDecoder decoder = new SmartThingsFeignErrorDecoder();

    @Test
    @DisplayName("외부 403을 내부 상태 예외로 보존한다")
    void keepsForbiddenStatus() {
        assertThat(decoder.decode("SmartThingsFeignClient#getDeviceStatus", response(403)))
                .isInstanceOf(SmartThingsApiException.class).extracting(e -> ((SmartThingsApiException) e).getStatus())
                .isEqualTo(403);
    }

    @Test
    @DisplayName("외부 429를 재시도 가능한 서버 오류 코드로 변환한다")
    void mapsRateLimit() {
        final var exception = SmartThingsErrorMapper.toStatusException(new SmartThingsApiException(429));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.SMARTTHINGS_RATE_LIMITED);
    }

    @Test
    @DisplayName("영구적인 4xx를 응답 오류 코드로 매핑한다")
    void mapsPermanentClientError() {
        final var exception = SmartThingsErrorMapper.toCommandException(new SmartThingsApiException(404));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.SMARTTHINGS_RESPONSE_INVALID);
    }

    @Test
    @DisplayName("확실히 거절된 명령은 claim을 해제한다")
    void releasesClaimWhenCommandIsDefinitelyRejected() {
        assertThat(SmartThingsErrorMapper
                .shouldReleaseCommandClaim(new team.washer.server.v2.global.common.error.exception.ErrorCodeException(
                        ErrorCode.SMARTTHINGS_RATE_LIMITED)))
                .isTrue();
        assertThat(SmartThingsErrorMapper
                .shouldReleaseCommandClaim(new team.washer.server.v2.global.common.error.exception.ErrorCodeException(
                        ErrorCode.SMARTTHINGS_COMMAND_UNAVAILABLE)))
                .isFalse();
    }

    @Test
    @DisplayName("외부 원문 없이 상태 조회와 명령 실패를 구분한다")
    void separatesOperationFailures() {
        final var status = SmartThingsErrorMapper.toStatusException(new RuntimeException("deviceId=secret"));
        final var command = SmartThingsErrorMapper.toCommandException(new RuntimeException("token=secret"));

        assertThat(status.getErrorCode()).isEqualTo(ErrorCode.SMARTTHINGS_STATUS_UNAVAILABLE);
        assertThat(command.getErrorCode()).isEqualTo(ErrorCode.SMARTTHINGS_COMMAND_UNAVAILABLE);
        assertThat(status.getMessage()).doesNotContain("secret");
        assertThat(command.getMessage()).doesNotContain("secret");
    }

    private Response response(final int status) {
        final var request = Request.create(Request.HttpMethod.GET,
                "https://api.smartthings.com/v1/devices/device-secret/status?access_token=secret",
                Map.of("Authorization", List.of("Bearer secret")),
                null,
                null,
                null);
        return Response.builder().request(request).status(status).reason("error").build();
    }
}
