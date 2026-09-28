package team.washer.server.v2.global.thirdparty.smartthings;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("SmartThingsOAuthStateStore는")
class SmartThingsOAuthStateStoreTest {

    private static final Instant BASE_TIME = Instant.parse("2026-09-13T00:00:00Z");

    @Nested
    @DisplayName("state를 검증할 때")
    class Describe_validateAndRemove {

        @Test
        @DisplayName("저장된 state를 한 번만 허용한다")
        void allowsStoredStateOnlyOnce() {
            final var stateStore = new SmartThingsOAuthStateStore(Clock.fixed(BASE_TIME, ZoneOffset.UTC));
            stateStore.save("state");

            assertThat(stateStore.validateAndRemove("state")).isTrue();
            assertThat(stateStore.validateAndRemove("state")).isFalse();
        }

        @Test
        @DisplayName("10분이 지나면 만료된 state를 거부한다")
        void rejectsExpiredState() {
            final var clock = new MutableClock(BASE_TIME);
            final var stateStore = new SmartThingsOAuthStateStore(clock);
            stateStore.save("state");
            clock.advanceSeconds(10 * 60L);

            assertThat(stateStore.validateAndRemove("state")).isFalse();
        }
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(final Instant instant) {
            this.instant = instant;
        }

        @Override
        public Instant instant() {
            return instant;
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(final java.time.ZoneId zone) {
            return this;
        }

        private void advanceSeconds(final long seconds) {
            instant = instant.plusSeconds(seconds);
        }
    }
}
