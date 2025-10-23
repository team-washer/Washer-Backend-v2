package team.washer.server.v2.global.thirdparty.feign.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import team.washer.server.v2.global.thirdparty.discord.data.DiscordWebhookPayload;
import team.washer.server.v2.global.thirdparty.feign.config.FeignConfig;

@FeignClient(name = "discordWebhook", url = "${third-party.discord.webhook-url}", configuration = FeignConfig.class)
public interface DiscordWebhookClient {
    @PostMapping
    void sendMessage(@RequestBody DiscordWebhookPayload payload);
}
