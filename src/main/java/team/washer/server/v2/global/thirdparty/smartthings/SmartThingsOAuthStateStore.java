package team.washer.server.v2.global.thirdparty.smartthings;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/**
 * SmartThings OAuth state 파라미터 저장소
 *
 * <p>
 * CSRF 공격 방지를 위해 생성된 state 값을 관리합니다.
 */
@Component
public class SmartThingsOAuthStateStore {

    private static final Duration STATE_TTL = Duration.ofMinutes(10);

    private final Clock clock;
    private final Map<String, Instant> validStates = new ConcurrentHashMap<>();

    public SmartThingsOAuthStateStore() {
        this(Clock.systemUTC());
    }

    SmartThingsOAuthStateStore(Clock clock) {
        this.clock = clock;
    }

    /**
     * state 값을 저장합니다.
     *
     * @param state
     *            저장할 state 값
     */
    public void save(String state) {
        final var now = Instant.now(clock);
        validStates.entrySet().removeIf(entry -> !now.isBefore(entry.getValue()));
        validStates.put(state, now.plus(STATE_TTL));
    }

    /**
     * state 값을 검증하고 제거합니다.
     *
     * @param state
     *            검증할 state 값
     * @return state가 유효하면 true, 그렇지 않으면 false
     */
    public boolean validateAndRemove(String state) {
        final var expiresAt = validStates.remove(state);
        return expiresAt != null && Instant.now(clock).isBefore(expiresAt);
    }
}
