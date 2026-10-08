package team.washer.server.v2.global.thirdparty.discord.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import feign.Request;
import feign.Retryer;

/**
 * Discord Webhook 호출의 대기 시간을 제한합니다.
 */
@Configuration
public class DiscordFeignConfig {

    public static final int CONNECT_TIMEOUT_MILLIS = 3000;
    public static final int READ_TIMEOUT_MILLIS = 5000;

    @Bean
    public Request.Options discordRequestOptions() {
        return new Request.Options(CONNECT_TIMEOUT_MILLIS, READ_TIMEOUT_MILLIS);
    }

    @Bean
    public Retryer discordRetryer() {
        return Retryer.NEVER_RETRY;
    }
}
