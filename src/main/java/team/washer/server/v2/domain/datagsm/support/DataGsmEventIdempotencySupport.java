package team.washer.server.v2.domain.datagsm.support;

import java.time.Duration;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import team.washer.server.v2.global.thirdparty.datagsm.config.DataGsmEventEnvironment;

@Component
@RequiredArgsConstructor
public class DataGsmEventIdempotencySupport {

    private static final String EVENT_KEY_PREFIX = "datagsm:event:";
    private static final String PROCESSED_VALUE = "processed";

    private final StringRedisTemplate redisTemplate;
    private final DataGsmEventEnvironment environment;

    public boolean isProcessed(String eventId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(createKey(eventId)));
    }

    public void markProcessed(String eventId) {
        redisTemplate.opsForValue()
                .set(createKey(eventId), PROCESSED_VALUE, Duration.ofDays(environment.idempotencyTtlDays()));
    }

    private String createKey(String eventId) {
        return EVENT_KEY_PREFIX + eventId;
    }
}
