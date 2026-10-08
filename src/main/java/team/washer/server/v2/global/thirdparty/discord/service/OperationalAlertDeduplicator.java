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
    private static final Duration FAILURE_RETRY_WINDOW = Duration.ofMinutes(1);

    private final Clock clock;
    private final Duration suppressionWindow;
    private final int maximumEntries;
    private final Map<String, Entry> entries = new HashMap<>();

    OperationalAlertDeduplicator(final Clock clock, final Duration suppressionWindow, final int maximumEntries) {
        this.clock = clock;
        this.suppressionWindow = suppressionWindow;
        this.maximumEntries = maximumEntries;
    }

    synchronized Decision reserve(final String key) {
        final var now = clock.instant();
        final var previous = entries.get(key);
        if (previous != null && previous.deliveryPending()) {
            entries.put(key, new Entry(previous.nextAllowedAt(), previous.suppressedCount() + 1, true));
            return Decision.suppressed();
        }
        if (previous != null && now.isBefore(previous.nextAllowedAt())) {
            entries.put(key, new Entry(previous.nextAllowedAt(), previous.suppressedCount() + 1, false));
            return Decision.suppressed();
        }

        removeExpired(now);
        if (previous == null && entries.size() >= maximumEntries) {
            return Decision.droppedByCapacity();
        }

        final var suppressedCount = previous == null ? 0 : previous.suppressedCount();
        entries.put(key, new Entry(now, suppressedCount, true));
        return Decision.send(suppressedCount);
    }

    synchronized void markDelivered(final String key) {
        final var pending = entries.get(key);
        if (pending == null || !pending.deliveryPending()) {
            return;
        }
        entries.put(key, new Entry(clock.instant().plus(suppressionWindow), 0, false));
    }

    synchronized void releaseFailedDelivery(final String key) {
        final var pending = entries.get(key);
        if (pending != null && pending.deliveryPending()) {
            entries.put(key, new Entry(clock.instant().plus(FAILURE_RETRY_WINDOW), pending.suppressedCount(), false));
        }
    }

    private void removeExpired(final Instant now) {
        entries.entrySet().removeIf(
                entry -> !entry.getValue().deliveryPending() && !now.isBefore(entry.getValue().nextAllowedAt()));
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

    private record Entry(Instant nextAllowedAt, long suppressedCount, boolean deliveryPending) {
    }
}
