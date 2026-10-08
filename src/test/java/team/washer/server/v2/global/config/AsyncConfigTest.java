package team.washer.server.v2.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("비동기 실행기 설정")
class AsyncConfigTest {

    @Test
    @DisplayName("운영 오류 알림은 하나의 bounded 전용 실행기를 사용한다")
    void createsBoundedOperationalAlertExecutor() {
        // Given
        final var config = new AsyncConfig();

        // When
        final var executor = config.operationalAlertTaskExecutor();
        executor.initialize();

        // Then
        try {
            assertThat(executor.getCorePoolSize()).isOne();
            assertThat(executor.getMaxPoolSize()).isOne();
            assertThat(executor.getThreadPoolExecutor().getQueue().remainingCapacity()).isEqualTo(100);
        } finally {
            executor.shutdown();
        }
    }
}
