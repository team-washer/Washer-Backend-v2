package team.washer.server.v2.global.thirdparty.discord.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Discord Feign 설정")
class DiscordFeignConfigTest {

    @Test
    @DisplayName("Webhook 연결과 읽기 시간을 제한하고 재시도하지 않는다")
    void configuresBoundedWebhookRequest() {
        // Given
        final var config = new DiscordFeignConfig();

        // When
        final var options = config.discordRequestOptions();

        // Then
        assertThat(options.connectTimeoutMillis()).isEqualTo(DiscordFeignConfig.CONNECT_TIMEOUT_MILLIS);
        assertThat(options.readTimeoutMillis()).isEqualTo(DiscordFeignConfig.READ_TIMEOUT_MILLIS);
        assertThat(config.discordRetryer()).isSameAs(feign.Retryer.NEVER_RETRY);
    }
}
