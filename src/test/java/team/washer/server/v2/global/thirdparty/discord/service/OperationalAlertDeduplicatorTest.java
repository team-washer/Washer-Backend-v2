package team.washer.server.v2.global.thirdparty.discord.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("운영 오류 중복 억제기")
class OperationalAlertDeduplicatorTest {

    @Test
    @DisplayName("중복 키를 억제하고 억제 시간을 지난 뒤 집계 횟수와 함께 재전송한다")
    void sendsAgainAfterSuppressionWindow() {
        // Given
        final var clock = new MutableClock(Instant.parse("2026-10-08T00:00:00Z"));
        final var deduplicator = new OperationalAlertDeduplicator(clock, Duration.ofMinutes(10), 2);

        // When
        final var first = deduplicator.reserve("same");
        deduplicator.markDelivered("same");
        final var suppressed = deduplicator.reserve("same");
        clock.advance(Duration.ofMinutes(10));
        final var retried = deduplicator.reserve("same");

        // Then
        assertThat(first.shouldSend()).isTrue();
        assertThat(suppressed.shouldSend()).isFalse();
        assertThat(suppressed.dropped()).isFalse();
        assertThat(retried.shouldSend()).isTrue();
        assertThat(retried.suppressedCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("만료되지 않은 서로 다른 키가 상한을 채우면 새 이벤트를 버린다")
    void boundsDeduplicationState() {
        // Given
        final var deduplicator = new OperationalAlertDeduplicator(Clock.fixed(Instant.parse("2026-10-08T00:00:00Z"),
                ZoneOffset.UTC), Duration.ofMinutes(10), 1);

        // When
        deduplicator.reserve("first");
        final var dropped = deduplicator.reserve("second");

        // Then
        assertThat(dropped.shouldSend()).isFalse();
        assertThat(dropped.dropped()).isTrue();
    }

    @Test
    @DisplayName("동시에 같은 이벤트가 들어와도 하나만 전송 대상으로 선택한다")
    void selectsOnlyOneConcurrentEventForDelivery() throws Exception {
        // Given
        final var deduplicator = new OperationalAlertDeduplicator(Clock.fixed(Instant.parse("2026-10-08T00:00:00Z"),
                ZoneOffset.UTC), Duration.ofMinutes(10), 10);
        final var ready = new CountDownLatch(8);
        final var start = new CountDownLatch(1);
        final var executor = Executors.newFixedThreadPool(8);
        final var decisions = new ArrayList<OperationalAlertDeduplicator.Decision>();

        // When
        try {
            final var futures = new ArrayList<java.util.concurrent.Future<OperationalAlertDeduplicator.Decision>>();
            for (int index = 0; index < 8; index++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return deduplicator.reserve("same");
                }));
            }
            ready.await();
            start.countDown();
            for (final var future : futures) {
                decisions.add(future.get());
            }
        } finally {
            executor.shutdownNow();
        }

        // Then
        assertThat(decisions).filteredOn(OperationalAlertDeduplicator.Decision::shouldSend).hasSize(1);
    }

    @Test
    @DisplayName("전송 대기 중 억제 횟수를 보존하고 전송 실패 뒤 재시도 대기 시간을 적용한다")
    void preservesSuppressedCountAndBacksOffAfterDeliveryFailure() {
        // Given
        final var clock = new MutableClock(Instant.parse("2026-10-08T00:00:00Z"));
        final var deduplicator = new OperationalAlertDeduplicator(clock, Duration.ofMinutes(10), 2);

        // When
        deduplicator.reserve("same");
        final var pendingSuppressed = deduplicator.reserve("same");
        deduplicator.releaseFailedDelivery("same");
        final var retrySuppressed = deduplicator.reserve("same");
        clock.advance(Duration.ofMinutes(1));
        final var retry = deduplicator.reserve("same");

        // Then
        assertThat(pendingSuppressed.shouldSend()).isFalse();
        assertThat(retrySuppressed.shouldSend()).isFalse();
        assertThat(retry.shouldSend()).isTrue();
        assertThat(retry.suppressedCount()).isEqualTo(2);
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(final Instant instant) {
            this.instant = instant;
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(final java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }

        private void advance(final Duration duration) {
            instant = instant.plus(duration);
        }
    }
}
