package team.washer.server.v2.global.thirdparty.feign.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cloud.openfeign.FeignAutoConfiguration;
import org.springframework.cloud.openfeign.FeignClientFactory;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import feign.Retryer;
import team.washer.server.v2.global.thirdparty.feign.client.DiscordWebhookClient;
import team.washer.server.v2.global.thirdparty.smartthings.config.SmartThingsFeignConfig;
import team.washer.server.v2.global.thirdparty.smartthings.feign.SmartThingsFeignClient;
import team.washer.server.v2.global.thirdparty.smartthings.feign.SmartThingsOAuthClient;

@DisplayName("Feign 클라이언트 설정 격리")
class FeignClientConfigIsolationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class, FeignAutoConfiguration.class))
            .withUserConfiguration(ScannedFeignConfiguration.class)
            .withPropertyValues("third-party.discord.webhook-url=http://localhost:1",
                    "third-party.smartthings.api-url=http://localhost:1",
                    "third-party.smartthings.oauth-url=http://localhost:1");

    @Nested
    @DisplayName("운영과 같이 Feign 설정 패키지를 컴포넌트 스캔하면")
    class ScannedConfiguration {

        @Test
        @DisplayName("모든 Feign 클라이언트가 Retryer 충돌 없이 생성된다")
        void createsAllClientsWithoutRetryerConflict() {
            // When & Then
            contextRunner.run(context -> {
                assertThat(context).hasNotFailed();
                // FactoryBean은 주입 시점에 클라이언트를 만들므로 실제 생성까지 확인한다
                assertThat(context.getBean(DiscordWebhookClient.class)).isNotNull();
                assertThat(context.getBean(SmartThingsFeignClient.class)).isNotNull();
                assertThat(context.getBean(SmartThingsOAuthClient.class)).isNotNull();
            });
        }

        @Test
        @DisplayName("SmartThings 전용 Retryer는 전역에 등록되지 않고 SmartThings 클라이언트에만 적용된다")
        void appliesSmartThingsRetryerOnlyToSmartThingsClients() {
            // When & Then
            contextRunner.run(context -> {
                final var clientFactory = context.getBean(FeignClientFactory.class);

                assertThat(context).doesNotHaveBean(Retryer.class);
                assertThat(clientFactory.getInstance("discordWebhook", Retryer.class)).isSameAs(Retryer.NEVER_RETRY);
                assertThat(clientFactory.getInstance("smartthings-api", Retryer.class))
                        .isInstanceOf(Retryer.Default.class);
                assertThat(clientFactory.getInstance("smartthings-oauth", Retryer.class))
                        .isInstanceOf(Retryer.Default.class);
            });
        }
    }

    /**
     * 애플리케이션의 컴포넌트 스캔처럼 Feign 설정 패키지에서 {@code @Configuration} 클래스만 등록한다.
     */
    @ComponentScan(basePackageClasses = {FeignConfig.class,
            SmartThingsFeignConfig.class}, useDefaultFilters = false, includeFilters = @ComponentScan.Filter(Configuration.class))
    static class ScannedFeignConfiguration {
    }
}
