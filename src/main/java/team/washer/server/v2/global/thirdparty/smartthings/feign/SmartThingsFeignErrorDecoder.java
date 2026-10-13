package team.washer.server.v2.global.thirdparty.smartthings.feign;

import feign.Response;
import feign.codec.ErrorDecoder;
import lombok.extern.slf4j.Slf4j;
import team.washer.server.v2.global.common.logging.SensitiveLogSanitizer;

/** SmartThings 응답 상태를 내부 예외로 변환하는 Feign 오류 디코더이다. */
@Slf4j
public class SmartThingsFeignErrorDecoder implements ErrorDecoder {

    @Override
    public Exception decode(final String methodKey, final Response response) {
        log.warn("smartthings api rejected request methodKey={} endpoint={} status={}",
                methodKey,
                SensitiveLogSanitizer.sanitizeEndpoint(response.request().url()),
                response.status());
        return new SmartThingsApiException(response.status());
    }
}
