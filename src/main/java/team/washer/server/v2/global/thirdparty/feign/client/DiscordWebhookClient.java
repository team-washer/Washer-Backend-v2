package team.washer.server.v2.global.thirdparty.feign.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import team.washer.server.v2.global.thirdparty.discord.data.DiscordWebhookPayload;

@FeignClient(name = "discordWebhook", url = "${third-party.discord.webhook-url}")
public interface DiscordWebhookClient {
    @PostMapping
    void sendMessage(@RequestBody DiscordWebhookPayload payload);
}
