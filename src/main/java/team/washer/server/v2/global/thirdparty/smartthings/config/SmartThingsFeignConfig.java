package team.washer.server.v2.global.thirdparty.smartthings.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import feign.Logger;
import feign.Request;
import feign.Retryer;
import feign.codec.Decoder;
import feign.codec.Encoder;
import feign.jackson.JacksonDecoder;
import feign.jackson.JacksonEncoder;

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
    public Encoder smartThingsEncoder() {
        return new JacksonEncoder();
    }

    @Bean
    public Decoder smartThingsDecoder() {
        return new JacksonDecoder();
    }
}
