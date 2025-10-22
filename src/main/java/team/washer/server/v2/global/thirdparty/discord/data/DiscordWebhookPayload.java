package team.washer.server.v2.global.thirdparty.discord.data;

import java.util.Collections;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DiscordWebhookPayload {
    @JsonProperty("embeds")
    private List<DiscordEmbed> embeds;

    @JsonProperty("content")
    private String content;

    public static DiscordWebhookPayload embedMessage(DiscordEmbed embed) {
        return DiscordWebhookPayload.builder().embeds(Collections.singletonList(embed)).build();
    }
}
