package team.washer.server.v2.global.thirdparty.discord.service;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.info.GitProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;
import team.washer.server.v2.global.common.logging.SensitiveLogSanitizer;
import team.washer.server.v2.global.config.AsyncConfig;
import team.washer.server.v2.global.thirdparty.discord.data.DiscordEmbed;
import team.washer.server.v2.global.thirdparty.discord.data.DiscordField;
import team.washer.server.v2.global.thirdparty.discord.data.DiscordWebhookPayload;
import team.washer.server.v2.global.thirdparty.discord.data.EmbedColor;
import team.washer.server.v2.global.thirdparty.feign.client.DiscordWebhookClient;

/**
 * 운영 오류를 안전한 요약 이벤트로 Discord에 전달합니다.
 */
@Slf4j
@Service
@Profile({"prod", "stage"})
public class DiscordErrorNotificationService {
    private static final int MAX_FIELD_LENGTH = 256;
    private static final int MAXIMUM_DEDUPLICATION_ENTRIES = 1000;
    private static final Duration SUPPRESSION_WINDOW = Duration.ofMinutes(10);
    private static final Set<String> SAFE_ADDITIONAL_INFO_KEYS = Set
            .of("HTTP Method", "Request Path", "Trace ID", "Error Code", "Operation", "Reservation ID", "Machine ID");

    private final DiscordWebhookClient discordWebhookClient;
    private final String environment;
    private final String deploymentCommit;
    private final Clock clock;
    private final OperationalAlertDeduplicator deduplicator;

    @Autowired
    public DiscordErrorNotificationService(final DiscordWebhookClient discordWebhookClient,
            final Environment environment,
            final ObjectProvider<GitProperties> gitPropertiesProvider) {
        this(discordWebhookClient, activeEnvironment(environment), abbreviatedCommit(gitPropertiesProvider),
                Clock.systemUTC());
    }

    public DiscordErrorNotificationService(final DiscordWebhookClient discordWebhookClient) {
        this(discordWebhookClient, "unknown", "unknown", Clock.systemUTC());
    }

    DiscordErrorNotificationService(final DiscordWebhookClient discordWebhookClient,
            final String environment,
            final String deploymentCommit,
            final Clock clock) {
        this.discordWebhookClient = discordWebhookClient;
        this.environment = environment;
        this.deploymentCommit = deploymentCommit;
        this.clock = clock;
        deduplicator = new OperationalAlertDeduplicator(clock, SUPPRESSION_WINDOW, MAXIMUM_DEDUPLICATION_ENTRIES);
    }

    @Async(AsyncConfig.OPERATIONAL_ALERT_TASK_EXECUTOR)
    public void notifyError(final Throwable exception, final String context, final Map<String, Object> additionalInfo) {
        final var event = createEvent(exception, additionalInfo);
        final var decision = deduplicator.register(deduplicationKey(event));
        if (!decision.shouldSend()) {
            if (decision.dropped()) {
                log.warn("operational alert dropped eventType={} reason=deduplication_capacity", event.eventType());
            }
            return;
        }
        try {
            final var embed = createErrorEmbed(event, decision.suppressedCount());
            discordWebhookClient.sendMessage(DiscordWebhookPayload.embedMessage(embed));
            log.info("operational alert sent eventType={} exceptionType={} fieldCount={}",
                    event.eventType(),
                    event.exceptionType(),
                    embed.getFields().size());
        } catch (final Exception sendException) {
            log.error("operational alert delivery failed eventType={} exceptionType={} sendExceptionType={}",
                    event.eventType(),
                    event.exceptionType(),
                    sendException.getClass().getSimpleName());
        }
    }

    @Async(AsyncConfig.OPERATIONAL_ALERT_TASK_EXECUTOR)
    public void notifyError(final Throwable exception) {
        notifyError(exception, null, Map.of());
    }

    private OperationalAlertEvent createEvent(final Throwable exception, final Map<String, Object> additionalInfo) {
        final var safeInfo = safeAdditionalInfo(additionalInfo);
        final var operation = safeInfo.remove("Operation");
        final var correlationId = safeInfo.remove("Trace ID");
        final var errorCode = safeInfo.remove("Error Code");
        final var eventType = safeInfo.containsKey("HTTP Method") ? "HTTP_SERVER_ERROR" : "OPERATIONAL_FAILURE";
        return new OperationalAlertEvent(eventType,
                "ERROR",
                clock.instant(),
                deploymentCommit,
                correlationId,
                operation == null ? eventType : operation,
                errorCode,
                exception.getClass().getSimpleName(),
                Map.copyOf(safeInfo));
    }

    private DiscordEmbed createErrorEmbed(final OperationalAlertEvent event, final long suppressedCount) {
        final List<DiscordField> fields = new ArrayList<>();
        fields.add(DiscordField.builder().name("Event Type").value(event.eventType()).inline(true).build());
        fields.add(DiscordField.builder().name("Severity").value(event.severity()).inline(true).build());
        fields.add(DiscordField.builder().name("Environment").value(environment).inline(true).build());
        addField(fields, "Git Commit", event.deploymentCommit());
        fields.add(DiscordField.builder().name("Exception Type").value(event.exceptionType()).inline(true).build());
        addField(fields, "Trace ID", event.correlationId());
        addField(fields, "Operation", event.operation());
        addField(fields, "Error Code", event.errorCode());
        event.metadata().forEach((key, value) -> fields
                .add(DiscordField.builder().name(key).value(truncateField(value)).inline(true).build()));
        if (suppressedCount > 0) {
            fields.add(DiscordField.builder().name("Suppressed Count").value(Long.toString(suppressedCount))
                    .inline(true).build());
        }
        return DiscordEmbed.builder().title("Operational alert").description("Inspect CloudWatch with Trace ID.")
                .color(EmbedColor.ERROR.getColor()).fields(fields).timestamp(event.occurredAt().toString()).build();
    }

    private static void addField(final List<DiscordField> fields, final String name, final String value) {
        if (value != null && !value.isBlank()) {
            fields.add(DiscordField.builder().name(name).value(value).inline(true).build());
        }
    }

    private static String activeEnvironment(final Environment environment) {
        final var profiles = environment.getActiveProfiles();
        return profiles.length == 0 ? "default" : String.join(",", profiles);
    }

    private static String abbreviatedCommit(final ObjectProvider<GitProperties> gitPropertiesProvider) {
        final var gitProperties = gitPropertiesProvider.getIfAvailable();
        return gitProperties == null ? "unknown" : gitProperties.getShortCommitId();
    }

    private static String deduplicationKey(final OperationalAlertEvent event) {
        return String.join("|",
                event.eventType(),
                event.exceptionType(),
                nullToEmpty(event.errorCode()),
                nullToEmpty(event.operation()),
                nullToEmpty(event.metadata().get("Request Path")),
                nullToEmpty(event.metadata().get("Reservation ID")),
                nullToEmpty(event.metadata().get("Machine ID")));
    }

    private Map<String, String> safeAdditionalInfo(final Map<String, Object> additionalInfo) {
        final Map<String, String> safeInfo = new LinkedHashMap<>();
        if (additionalInfo == null || additionalInfo.isEmpty()) {
            return safeInfo;
        }
        additionalInfo.forEach((key, value) -> {
            if (SAFE_ADDITIONAL_INFO_KEYS.contains(key) && value != null) {
                final String safeValue = "Request Path".equals(key)
                        ? SensitiveLogSanitizer.sanitizeEndpoint(value.toString())
                        : value.toString();
                final var sanitized = sanitizeAndTruncate(safeValue);
                if (sanitized != null) {
                    safeInfo.put(key, sanitized);
                }
            }
        });
        return safeInfo;
    }

    private static String nullToEmpty(final String value) {
        return value == null ? "" : value;
    }

    private String truncateField(final String text) {
        if (text.length() > MAX_FIELD_LENGTH) {
            return text.substring(0, MAX_FIELD_LENGTH) + "...";
        }
        return text;
    }

    private String sanitizeAndTruncate(final String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        return truncateField(SensitiveLogSanitizer.sanitize(text));
    }
}
