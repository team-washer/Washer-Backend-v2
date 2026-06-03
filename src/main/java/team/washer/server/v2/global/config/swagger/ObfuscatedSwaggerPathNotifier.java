package team.washer.server.v2.global.config.swagger;

import java.time.Instant;
import java.util.List;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import team.washer.server.v2.global.thirdparty.discord.data.DiscordEmbed;
import team.washer.server.v2.global.thirdparty.discord.data.DiscordField;
import team.washer.server.v2.global.thirdparty.discord.data.DiscordWebhookPayload;
import team.washer.server.v2.global.thirdparty.discord.data.EmbedColor;
import team.washer.server.v2.global.thirdparty.feign.client.DiscordWebhookClient;

/**
 * prod 기동 시 {@link SwaggerPathObfuscator}가 난독화한 Swagger 경로를 Discord로 통지하는 컴포넌트
 */
@Slf4j
@Component
@Profile("prod")
@RequiredArgsConstructor
public class ObfuscatedSwaggerPathNotifier {

    private final Environment environment;
    private final DiscordWebhookClient discordWebhookClient;

    @EventListener(ApplicationReadyEvent.class)
    public void notifyObfuscatedSwaggerPath() {
        // Swagger UI가 비활성화된 경우 실제로 열리지 않는 경로를 통지하지 않도록 건너뛴다.
        final var swaggerUiEnabled = environment
                .getProperty("springdoc.swagger-ui.enabled", Boolean.class, Boolean.TRUE);
        if (!swaggerUiEnabled) {
            log.info("swagger-ui disabled; skip obfuscated swagger path notify");
            return;
        }

        final var swaggerUiPath = environment.getProperty("springdoc.swagger-ui.path", "/swagger-ui.html");
        final var apiDocsPath = environment.getProperty("springdoc.api-docs.path", "/v3/api-docs");

        log.info("obfuscated swagger paths swaggerUi={} apiDocs={}", swaggerUiPath, apiDocsPath);

        try {
            final var embed = DiscordEmbed.builder().title("🔐 Swagger 경로 난독화 적용")
                    .description("이번 부팅에 적용된 Swagger 문서 경로입니다. 외부에 노출되지 않도록 주의하세요.")
                    .color(EmbedColor.WARNING.getColor())
                    .fields(List.of(
                            DiscordField.builder().name("Swagger UI").value("`" + swaggerUiPath + "`").inline(false)
                                    .build(),
                            DiscordField.builder().name("API Docs").value("`" + apiDocsPath + "`").inline(false)
                                    .build()))
                    .timestamp(Instant.now().toString()).build();

            discordWebhookClient.sendMessage(DiscordWebhookPayload.embedMessage(embed));
        } catch (final Exception e) {
            log.error("failed to notify obfuscated swagger path to discord", e);
        }
    }
}
