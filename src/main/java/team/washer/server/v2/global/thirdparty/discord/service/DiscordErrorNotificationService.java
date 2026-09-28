package team.washer.server.v2.global.thirdparty.discord.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import team.washer.server.v2.global.common.logging.SensitiveLogSanitizer;
import team.washer.server.v2.global.thirdparty.discord.data.DiscordEmbed;
import team.washer.server.v2.global.thirdparty.discord.data.DiscordField;
import team.washer.server.v2.global.thirdparty.discord.data.DiscordWebhookPayload;
import team.washer.server.v2.global.thirdparty.discord.data.EmbedColor;
import team.washer.server.v2.global.thirdparty.feign.client.DiscordWebhookClient;
import team.washer.server.v2.global.thirdparty.feign.error.FeignErrorDecoder;

@Slf4j
@Service
@Profile({"prod", "stage"})
@RequiredArgsConstructor
public class DiscordErrorNotificationService {
    private static final int MAX_FIELD_LENGTH = 1000;
    private static final Set<String> SAFE_ADDITIONAL_INFO_KEYS = Set
            .of("HTTP Method", "Request Path", "Trace ID", "감지된 기기", "조치 필요", "Penalty Type");

    private final DiscordWebhookClient discordWebhookClient;

    @Async
    public void notifyError(Throwable exception, String context, Map<String, Object> additionalInfo) {
        try {
            DiscordEmbed embed = createErrorEmbed(exception, context, additionalInfo);
            DiscordWebhookPayload payload = DiscordWebhookPayload.embedMessage(embed);

            discordWebhookClient.sendMessage(payload);
            log.info("discord error notification sent exceptionType={} fieldCount={}",
                    exception.getClass().getSimpleName(),
                    embed.getFields().size());
        } catch (Exception sendException) {
            log.error("discord error notification failed exceptionType={} sendExceptionType={}",
                    exception.getClass().getSimpleName(),
                    sendException.getClass().getSimpleName());
        }
    }

    @Async
    public void notifyError(Throwable exception) {
        notifyError(exception, null, Map.of());
    }

    private DiscordEmbed createErrorEmbed(final Throwable exception,
            final String context,
            final Map<String, Object> additionalInfo) {
        final List<DiscordField> fields = new ArrayList<>();
        fields.add(DiscordField.builder().name("Exception Type").value(exception.getClass().getSimpleName())
                .inline(true).build());
        if (context != null) {
            fields.add(DiscordField.builder().name("Context")
                    .value(SensitiveLogSanitizer.sanitize(truncateField(context))).inline(false).build());
        }
        final StackTraceElement firstElement = exception.getStackTrace().length > 0
                ? exception.getStackTrace()[0]
                : null;
        if (firstElement != null) {
            final String location = String.format("```%s:%d (%s)```",
                    firstElement.getFileName(),
                    firstElement.getLineNumber(),
                    firstElement.getMethodName());
            fields.add(DiscordField.builder().name("Location").value(location).inline(false).build());
        }
        safeAdditionalInfo(additionalInfo).forEach((key, value) -> fields
                .add(DiscordField.builder().name(key).value(truncateField(value)).inline(true).build()));

        final StringBuilder stackTrace = new StringBuilder();
        final int limit = Math.min(5, exception.getStackTrace().length);
        for (int i = 0; i < limit; i++) {
            final StackTraceElement element = exception.getStackTrace()[i];
            stackTrace.append(String.format("at %s.%s(%s:%d)\n",
                    element.getClassName(),
                    element.getMethodName(),
                    element.getFileName(),
                    element.getLineNumber()));
        }

        if (!stackTrace.isEmpty()) {
            fields.add(
                    DiscordField.builder().name("Stack Trace").value("```" + stackTrace + "```").inline(false).build());
        }
        return DiscordEmbed.builder().title("🚨 애플리케이션 에러 발생")
                .description("Exception type: " + exception.getClass().getName()).color(EmbedColor.ERROR.getColor())
                .fields(fields).timestamp(Instant.now().toString()).build();
    }

    private Map<String, String> safeAdditionalInfo(final Map<String, Object> additionalInfo) {
        if (additionalInfo == null || additionalInfo.isEmpty()) {
            return Map.of();
        }
        final Map<String, String> safeInfo = new LinkedHashMap<>();
        additionalInfo.forEach((key, value) -> {
            if (SAFE_ADDITIONAL_INFO_KEYS.contains(key) && value != null) {
                final String safeValue = "Request Path".equals(key)
                        ? FeignErrorDecoder.safeEndpoint(value.toString())
                        : value.toString();
                safeInfo.put(key, safeValue);
            }
        });
        return safeInfo;
    }

    private String truncateField(String text) {
        if (text.length() > MAX_FIELD_LENGTH) {
            return text.substring(0, MAX_FIELD_LENGTH) + "...";
        }
        return text;
    }
}
