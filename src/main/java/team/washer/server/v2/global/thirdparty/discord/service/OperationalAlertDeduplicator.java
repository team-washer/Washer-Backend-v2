package team.washer.server.v2.global.thirdparty.discord.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * 인스턴스 안에서 반복되는 같은 운영 이벤트를 제한합니다.
 */
final class OperationalAlertDeduplicator {
    private final Clock clock;
    private final Duration suppressionWindow;
    private final int maximumEntries;
    private final Map<String, Entry> entries = new HashMap<>();

    OperationalAlertDeduplicator(final Clock clock, final Duration suppressionWindow, final int maximumEntries) {
        this.clock = clock;
        this.suppressionWindow = suppressionWindow;
        this.maximumEntries = maximumEntries;
    }

    synchronized Decision register(final String key) {
        final var now = clock.instant();
        final var previous = entries.get(key);
        if (previous != null && now.isBefore(previous.sentAt().plus(suppressionWindow))) {
            entries.put(key, new Entry(previous.sentAt(), previous.suppressedCount() + 1));
            return Decision.suppressed();
        }

        removeExpired(now);
        if (previous == null && entries.size() >= maximumEntries) {
            return Decision.droppedByCapacity();
        }

        final var suppressedCount = previous == null ? 0 : previous.suppressedCount();
        entries.put(key, new Entry(now, 0));
        return Decision.send(suppressedCount);
    }

    private void removeExpired(final Instant now) {
        entries.entrySet().removeIf(entry -> !now.isBefore(entry.getValue().sentAt().plus(suppressionWindow)));
    }

    record Decision(boolean shouldSend, boolean dropped, long suppressedCount) {
        static Decision send(final long suppressedCount) {
            return new Decision(true, false, suppressedCount);
        }

        static Decision suppressed() {
            return new Decision(false, false, 0);
        }

        static Decision droppedByCapacity() {
            return new Decision(false, true, 0);
        }
    }

    private record Entry(Instant sentAt, long suppressedCount) {
    }
}
