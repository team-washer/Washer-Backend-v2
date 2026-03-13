package team.washer.server.v2.global.thirdparty.smartthings.feign;

import feign.Response;
import lombok.extern.slf4j.Slf4j;
import team.washer.server.v2.domain.smartthings.exception.SmartThingsPermissionException;
import team.washer.server.v2.global.thirdparty.feign.error.FeignErrorDecoder;

/**
 * SmartThings 전용 Feign 에러 디코더. 403 Forbidden 응답을
 * {@link SmartThingsPermissionException}으로 변환하여 일반적인 권한 오류와 SmartThings 권한 오류를
 * 구분합니다.
 */
@Slf4j
public class SmartThingsFeignErrorDecoder extends FeignErrorDecoder {

    @Override
    public Exception decode(String methodKey, Response response) {
        if (response.status() == 403) {
            log.warn("[SmartThings] 권한 오류 발생 (403 Forbidden). 메서드: {}, URL: {}", methodKey, response.request().url());
            return new SmartThingsPermissionException(
                    "SmartThings API 권한이 없습니다. OAuth 스코프(x:devices:*) 또는 기기 접근 권한을 확인해주세요.");
        }
        return super.decode(methodKey, response);
    }
}
