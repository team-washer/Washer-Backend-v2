package team.washer.server.v2.global.thirdparty.smartthings.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import feign.Logger;
import feign.Request;
import feign.Retryer;
import feign.codec.EncodeException;
import feign.codec.Encoder;
import tools.jackson.databind.ObjectMapper;

@Configuration
public class SmartThingsFeignConfig {

    private static final int CONNECT_TIMEOUT = 5000;
    private static final int READ_TIMEOUT = 5000;
    private static final int MAX_ATTEMPTS = 2;

    @Bean
    public Request.Options smartThingsRequestOptions() {
        return new Request.Options(CONNECT_TIMEOUT, READ_TIMEOUT);
    }

    @Bean
    public Retryer smartThingsRetryer() {
        return new Retryer.Default(100, 1000, MAX_ATTEMPTS);
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
