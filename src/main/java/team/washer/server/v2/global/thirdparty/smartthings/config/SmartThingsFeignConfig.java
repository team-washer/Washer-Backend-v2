package team.washer.server.v2.global.thirdparty.smartthings.config;

import org.springframework.context.annotation.Bean;

import feign.Logger;
import feign.Request;
import feign.Retryer;
import feign.codec.EncodeException;
import feign.codec.Encoder;
import feign.codec.ErrorDecoder;
import team.washer.server.v2.global.thirdparty.smartthings.feign.SmartThingsFeignErrorDecoder;
import tools.jackson.databind.ObjectMapper;

/**
 * SmartThings Feign 클라이언트 전용 설정입니다.
 *
 * <p>
 * {@code @FeignClient(configuration = ...)}로 지정한 클라이언트에만 적용합니다.
 * {@code @Configuration}을 붙여 컴포넌트 스캔 대상이 되면 재시도·타임아웃 빈이 모든 Feign 클라이언트로 퍼지고, 다른
 * 클라이언트의 전용 빈과 충돌해 애플리케이션이 기동하지 못합니다.
 * </p>
 */
public class SmartThingsFeignConfig {

    public static final int CONNECT_TIMEOUT_MILLIS = 5000;
    public static final int READ_TIMEOUT_MILLIS = 5000;
    public static final int MAX_ATTEMPTS = 2;
    public static final int MAX_RETRY_BACKOFF_MILLIS = 1000;
    public static final long MAX_REQUEST_LIFETIME_MILLIS = (long) MAX_ATTEMPTS
            * (CONNECT_TIMEOUT_MILLIS + READ_TIMEOUT_MILLIS) + (long) (MAX_ATTEMPTS - 1) * MAX_RETRY_BACKOFF_MILLIS;

    @Bean
    public Request.Options smartThingsRequestOptions() {
        return new Request.Options(CONNECT_TIMEOUT_MILLIS, READ_TIMEOUT_MILLIS);
    }

    @Bean
    public Retryer smartThingsRetryer() {
        return new Retryer.Default(100, 1000, MAX_ATTEMPTS);
    }

    @Bean
    public ErrorDecoder smartThingsFeignErrorDecoder() {
        return new SmartThingsFeignErrorDecoder();
    }

    @Bean
    public Logger.Level smartThingsFeignLoggerLevel() {
        return Logger.Level.BASIC;
    }

    @Bean
    public Encoder smartThingsFeignEncoder(ObjectMapper objectMapper) {
        return (object, bodyType, template) -> {
            try {
                if (object instanceof String s) {
                    template.body(s);
                } else {
                    template.body(objectMapper.writeValueAsString(object));
                }
            } catch (Exception e) {
                throw new EncodeException("요청 바디 직렬화에 실패했습니다", e);
            }
        };
    }
}
