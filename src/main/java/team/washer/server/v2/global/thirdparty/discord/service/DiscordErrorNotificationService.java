package team.washer.server.v2.global.thirdparty.discord.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import team.washer.server.v2.global.thirdparty.discord.data.DiscordEmbed;
import team.washer.server.v2.global.thirdparty.discord.data.DiscordField;
import team.washer.server.v2.global.thirdparty.discord.data.DiscordWebhookPayload;
import team.washer.server.v2.global.thirdparty.discord.data.EmbedColor;
import team.washer.server.v2.global.thirdparty.feign.client.DiscordWebhookClient;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Service
@Profile({"prod", "stage"})
@RequiredArgsConstructor
public class DiscordErrorNotificationService {
    private static final int MAX_FIELD_LENGTH = 1000;

    private final DiscordWebhookClient discordWebhookClient;
    private final ObjectMapper objectMapper;

    @Async
    public void notifyError(Throwable exception, String context, Map<String, Object> additionalInfo) {
        try {
            DiscordEmbed embed = createErrorEmbed(exception, context, additionalInfo);
            DiscordWebhookPayload payload = DiscordWebhookPayload.embedMessage(embed);

            String jsonPayload = objectMapper.writeValueAsString(payload);
            log.info("Discord 웹훅 전송 페이로드: {}", jsonPayload);

            discordWebhookClient.sendMessage(payload);
        } catch (Exception sendException) {
            log.error("Discord 에러 알림 전송 실패", sendException);
        }
    }

    @Async
    public void notifyError(Throwable exception) {
        notifyError(exception, null, Map.of());
    }

    private DiscordEmbed createErrorEmbed(Throwable exception, String context, Map<String, Object> additionalInfo) {
        List<DiscordField> fields = new ArrayList<>();
        fields.add(DiscordField.builder().name("Exception Type").value(exception.getClass().getSimpleName())
                .inline(true).build());
        String message = exception.getMessage() != null ? exception.getMessage() : "No message";
        fields.add(DiscordField.builder().name("Message").value(truncateField(message)).inline(false).build());
        if (context != null) {
            fields.add(DiscordField.builder().name("Context").value(truncateField(context)).inline(false).build());
        }
        StackTraceElement firstElement = exception.getStackTrace().length > 0 ? exception.getStackTrace()[0] : null;
        if (firstElement != null) {
            String location = String.format("```%s:%d (%s)```", firstElement.getFileName(),
                    firstElement.getLineNumber(), firstElement.getMethodName());
            fields.add(DiscordField.builder().name("Location").value(location).inline(false).build());
        }
        if (additionalInfo != null && !additionalInfo.isEmpty()) {
            additionalInfo.forEach((key, value) -> fields.add(
                    DiscordField.builder().name(key).value(truncateField(value.toString())).inline(true).build()));
        }
        StringBuilder stackTrace = new StringBuilder();
        int limit = Math.min(5, exception.getStackTrace().length);
        for (int i = 0; i < limit; i++) {
            StackTraceElement element = exception.getStackTrace()[i];
            stackTrace.append(String.format("at %s.%s(%s:%d)\n", element.getClassName(), element.getMethodName(),
                    element.getFileName(), element.getLineNumber()));
        }

        if (!stackTrace.isEmpty()) {
            fields.add(DiscordField.builder().name("Stack Trace").value("```" + stackTrace + "```")
                    .inline(false).build());
        }
        return DiscordEmbed.builder().title("🚨 애플리케이션 에러 발생")
                .description(exception.getClass().getName() + ": " + message).color(EmbedColor.ERROR.getColor())
                .fields(fields).timestamp(Instant.now().toString()).build();
    }

    private String truncateField(String text) {
        if (text.length() > MAX_FIELD_LENGTH) {
            return text.substring(0, MAX_FIELD_LENGTH) + "...";
        }
        return text;
    }
}
