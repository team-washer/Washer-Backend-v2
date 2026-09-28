package team.washer.server.v2.global.thirdparty.feign.error;

import org.springframework.http.HttpStatus;

import feign.FeignException;
import feign.Response;
import feign.codec.ErrorDecoder;
import lombok.extern.slf4j.Slf4j;
import team.themoment.sdk.exception.ExpectedException;
import team.washer.server.v2.global.common.logging.SensitiveLogSanitizer;

@Slf4j
public class FeignErrorDecoder implements ErrorDecoder {

    @Override
    public Exception decode(final String methodKey, final Response response) {
        final int status = response.status();

        if (status >= 400) {
            logFeignError(methodKey, response);
            return createExpectedException(status);
        }
        return FeignException.errorStatus(methodKey, response);
    }

    private void logFeignError(final String methodKey, final Response response) {
        log.error("feign request failed methodKey={} httpMethod={} endpoint={} status={}",
                methodKey,
                response.request().httpMethod().name(),
                SensitiveLogSanitizer.sanitizeEndpoint(response.request().url()),
                response.status());
    }

    private ExpectedException createExpectedException(int status) {
        return switch (status) {
            case 400 -> new ExpectedException("잘못된 요청입니다.", HttpStatus.BAD_REQUEST);
            case 401 -> new ExpectedException("인증이 필요합니다.", HttpStatus.UNAUTHORIZED);
            case 403 -> new ExpectedException("접근이 거부되었습니다.", HttpStatus.FORBIDDEN);
            case 404 -> new ExpectedException("요청하신 리소스를 찾을 수 없습니다.", HttpStatus.NOT_FOUND);
            case 429 -> new ExpectedException("요청이 너무 많습니다. 잠시 후 다시 시도해 주세요.", HttpStatus.TOO_MANY_REQUESTS);
            case 500 -> new ExpectedException("외부 서비스 내부 오류가 발생했습니다.", HttpStatus.INTERNAL_SERVER_ERROR);
            case 502 -> new ExpectedException("게이트웨이 오류가 발생했습니다.", HttpStatus.BAD_GATEWAY);
            case 503 ->
                new ExpectedException("서비스를 일시적으로 사용할 수 없습니다. 잠시 후 다시 시도해 주세요.", HttpStatus.SERVICE_UNAVAILABLE);
            default -> new ExpectedException("외부 요청 처리 중 오류가 발생했습니다.", HttpStatus.INTERNAL_SERVER_ERROR);
        };
    }
}
