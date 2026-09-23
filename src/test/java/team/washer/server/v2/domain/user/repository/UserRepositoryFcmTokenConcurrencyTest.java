package team.washer.server.v2.domain.user.repository;

import static org.assertj.core.api.Assertions.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import team.washer.server.v2.domain.user.entity.User;
import team.washer.server.v2.global.config.JpaAuditingConfig;
import team.washer.server.v2.global.config.QueryDslConfig;

@DataJpaTest
@Import({JpaAuditingConfig.class, QueryDslConfig.class})
@TestPropertySource(properties = {"spring.datasource.url=jdbc:h2:mem:fcm_token_concurrency_test;DB_CLOSE_DELAY=-1",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.docker.compose.enabled=false"})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DisplayName("UserRepository FCM 토큰 조건부 삭제 동시성 테스트")
class UserRepositoryFcmTokenConcurrencyTest {

    private static final String OLD_TOKEN = "old-fcm-token";
    private static final String NEW_TOKEN = "new-fcm-token";

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    @DisplayName("이전 전송 실패 정리가 늦게 실행되어도 새로 등록된 토큰을 삭제하지 않아야 한다")
    void it_preserves_new_token_when_stale_cleanup_runs_after_registration() throws Exception {
        // Given
        final TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        final Long userId = transactionTemplate.execute(status -> {
            final User user = entityManager.persist(
                    User.builder().name("김철수").studentId("20210001").roomNumber("301").grade(3).floor(3).build());
            user.updateFcmToken(OLD_TOKEN);
            entityManager.flush();
            return user.getId();
        });
        final CountDownLatch cleanupStarted = new CountDownLatch(1);
        final CountDownLatch allowCleanup = new CountDownLatch(1);
        final ExecutorService executor = Executors.newSingleThreadExecutor();

        try {
            // When
            final Future<Integer> cleanupResult = executor.submit(() -> transactionTemplate.execute(status -> {
                cleanupStarted.countDown();
                await(allowCleanup);
                return userRepository.clearFcmTokenIfMatches(userId, OLD_TOKEN);
            }));

            assertThat(cleanupStarted.await(5, TimeUnit.SECONDS)).isTrue();
            transactionTemplate.executeWithoutResult(status -> {
                final User user = userRepository.findById(userId).orElseThrow();
                user.updateFcmToken(NEW_TOKEN);
            });
            allowCleanup.countDown();

            // Then
            assertThat(cleanupResult.get(5, TimeUnit.SECONDS)).isZero();
            assertThat(
                    transactionTemplate.execute(status -> userRepository.findById(userId).orElseThrow()).getFcmToken())
                    .isEqualTo(NEW_TOKEN);
        } finally {
            executor.shutdownNow();
        }
    }

    private void await(final CountDownLatch latch) {
        try {
            assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("동시성 테스트 대기 중 인터럽트가 발생했습니다.", e);
        }
    }
}
