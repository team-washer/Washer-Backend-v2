package team.washer.server.v2.global.thirdparty.smartthings.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import feign.Logger;
import feign.Request;
import feign.Retryer;
import feign.codec.EncodeException;
import feign.codec.Encoder;
import feign.codec.ErrorDecoder;
import team.washer.server.v2.global.thirdparty.smartthings.feign.SmartThingsFeignErrorDecoder;
import tools.jackson.databind.ObjectMapper;

@Configuration
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
