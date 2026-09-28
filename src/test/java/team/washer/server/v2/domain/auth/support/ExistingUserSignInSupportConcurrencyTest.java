package team.washer.server.v2.domain.auth.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.util.Optional;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import team.washer.server.v2.domain.auth.dto.response.TokenResDto;
import team.washer.server.v2.domain.user.entity.User;
import team.washer.server.v2.domain.user.enums.UserRole;
import team.washer.server.v2.domain.user.repository.UserRepository;
import team.washer.server.v2.global.config.JpaAuditingConfig;
import team.washer.server.v2.global.config.QueryDslConfig;

@DataJpaTest
@Import({JpaAuditingConfig.class, QueryDslConfig.class, ExistingUserSignInSupport.class})
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:existing_user_sign_in_concurrency_test;DB_CLOSE_DELAY=-1",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.docker.compose.enabled=false"})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DisplayName("ExistingUserSignInSupport 동시성 테스트")
class ExistingUserSignInSupportConcurrencyTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private ExistingUserSignInSupport existingUserSignInSupport;

    @MockitoBean
    private TokenGenerationSupport tokenGenerationSupport;

    @Test
    @DisplayName("탈퇴가 사용자 잠금을 보유한 동안 기존 사용자 로그인은 토큰을 발급하지 않아야 한다")
    void waitsForWithdrawalLockBeforeGeneratingToken() throws Exception {
        final TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        final Long userId = transactionTemplate.execute(status -> {
            final User user = entityManager.persist(User.builder().name("테스트 사용자").studentId("20210001")
                    .roomNumber("301").grade(3).floor(3).role(UserRole.USER).build());
            entityManager.flush();
            return user.getId();
        });
        final CountDownLatch withdrawalLockAcquired = new CountDownLatch(1);
        final CountDownLatch allowWithdrawalCommit = new CountDownLatch(1);
        final CountDownLatch signInStarted = new CountDownLatch(1);
        final ExecutorService executor = Executors.newFixedThreadPool(2);
        final var expectedTokens = new TokenResDto("access.token", 3600L, "refresh.token");
        given(tokenGenerationSupport.generate(anyLong(), any(UserRole.class))).willReturn(expectedTokens);

        try {
            final Future<?> withdrawal = executor.submit(() -> transactionTemplate.executeWithoutResult(status -> {
                userRepository.findByIdForUpdate(userId).orElseThrow();
                withdrawalLockAcquired.countDown();
                await(allowWithdrawalCommit);
            }));

            assertThat(withdrawalLockAcquired.await(5, TimeUnit.SECONDS)).isTrue();
            final Future<Optional<TokenResDto>> signIn = executor.submit(() -> {
                signInStarted.countDown();
                return existingUserSignInSupport.generateIfExistingUser("20210001");
            });

            assertThat(signInStarted.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(signIn).isNotDone();

            allowWithdrawalCommit.countDown();

            assertThat(signIn.get(5, TimeUnit.SECONDS)).contains(expectedTokens);
            withdrawal.get(5, TimeUnit.SECONDS);
            verify(tokenGenerationSupport).generate(1L, UserRole.USER);
        } finally {
            allowWithdrawalCommit.countDown();
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
