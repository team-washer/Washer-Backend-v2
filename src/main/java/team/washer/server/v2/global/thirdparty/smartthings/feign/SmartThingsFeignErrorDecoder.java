package team.washer.server.v2.global.thirdparty.smartthings.feign;

import feign.Response;
import lombok.extern.slf4j.Slf4j;
import team.washer.server.v2.domain.smartthings.exception.SmartThingsPermissionException;
import team.washer.server.v2.global.common.logging.SensitiveLogSanitizer;
import team.washer.server.v2.global.thirdparty.feign.error.FeignErrorDecoder;

/**
 * SmartThings 전용 Feign 에러 디코더. 403 Forbidden 응답을
 * {@link SmartThingsPermissionException}으로 변환하여 일반적인 권한 오류와 SmartThings 권한 오류를
 * 구분합니다.
 */
@Slf4j
public class SmartThingsFeignErrorDecoder extends FeignErrorDecoder {

    @Override
    public Exception decode(final String methodKey, final Response response) {
        if (response.status() == 403) {
            log.warn("smartthings permission denied methodKey={} endpoint={} status={}",
                    methodKey,
                    SensitiveLogSanitizer.sanitizeEndpoint(response.request().url()),
                    response.status());
            return new SmartThingsPermissionException(
                    "SmartThings API 권한이 없습니다. OAuth 스코프(x:devices:*) 또는 기기 접근 권한을 확인해주세요.");
        }
        return super.decode(methodKey, response);
    }
}
