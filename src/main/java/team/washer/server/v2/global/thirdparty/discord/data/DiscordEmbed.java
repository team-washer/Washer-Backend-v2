package team.washer.server.v2.global.thirdparty.discord.data;

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
public class DiscordEmbed {
    @JsonProperty("title")
    private String title;

    @JsonProperty("color")
    private Integer color;

    @JsonProperty("fields")
    private List<DiscordField> fields;

    @JsonProperty("timestamp")
    private String timestamp;
}
