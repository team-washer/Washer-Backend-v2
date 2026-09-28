package team.washer.server.v2.global.thirdparty.datagsm.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "third-party.datagsm.event")
public record DataGsmEventEnvironment(String secret, long idempotencyTtlDays) {
    private static final long DEFAULT_IDEMPOTENCY_TTL_DAYS = 30;

    public DataGsmEventEnvironment {
        if (idempotencyTtlDays <= 0) {
            idempotencyTtlDays = DEFAULT_IDEMPOTENCY_TTL_DAYS;
        }
    }
}
