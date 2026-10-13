package team.washer.server.v2.global.common.transaction;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

@DisplayName("AfterCommitExecutor 클래스의")
class AfterCommitExecutorTest {

    private DataSourceTransactionManager transactionManager;
    private TransactionTemplate transactionTemplate;
    private AfterCommitExecutor afterCommitExecutor;

    @BeforeEach
    void setUp() {
        final var dataSource = new DriverManagerDataSource("jdbc:h2:mem:after-commit-executor;DB_CLOSE_DELAY=-1",
                "sa",
                "");
        transactionManager = new DataSourceTransactionManager(dataSource);
        transactionTemplate = new TransactionTemplate(transactionManager);
        afterCommitExecutor = new AfterCommitExecutor(transactionManager);
    }

    @Nested
    @DisplayName("execute 메서드는")
    class Describe_execute {

        @Nested
        @DisplayName("트랜잭션 밖에서 호출하면")
        class Context_without_transaction {

            @Test
            @DisplayName("작업을 즉시 실행해야 한다")
            void it_runs_task_immediately() {
                // Given
                final var executionCount = new AtomicInteger();

                // When
                afterCommitExecutor.execute(executionCount::incrementAndGet);

                // Then
                assertThat(executionCount).hasValue(1);
            }
        }

        @Nested
        @DisplayName("트랜잭션이 커밋되면")
        class Context_with_commit {

            @Test
            @DisplayName("커밋 전에는 실행하지 않고 커밋 이후에 한 번 실행해야 한다")
            void it_runs_task_once_after_commit() {
                // Given
                final var executionCount = new AtomicInteger();
                final var countBeforeCommit = new AtomicInteger(-1);

                // When
                transactionTemplate.executeWithoutResult(status -> {
                    afterCommitExecutor.execute(executionCount::incrementAndGet);
                    countBeforeCommit.set(executionCount.get());
                });

                // Then
                assertThat(countBeforeCommit).hasValue(0);
                assertThat(executionCount).hasValue(1);
            }

            @Test
            @DisplayName("커밋된 트랜잭션을 중단한 상태에서 작업을 실행해야 한다")
            void it_runs_task_with_committed_transaction_suspended() {
                // Given
                final var transactionActive = new AtomicBoolean(true);
                final var connectionBound = new AtomicBoolean(true);

                // When
                transactionTemplate.executeWithoutResult(status -> afterCommitExecutor.execute(() -> {
                    transactionActive.set(TransactionSynchronizationManager.isActualTransactionActive());
                    connectionBound
                            .set(TransactionSynchronizationManager.hasResource(transactionManager.getDataSource()));
                }));

                // Then
                assertThat(transactionActive).isFalse();
                assertThat(connectionBound).isFalse();
            }

            @Test
            @DisplayName("작업 안의 REQUIRED 트랜잭션을 새 트랜잭션으로 시작해야 한다")
            void it_starts_new_transaction_for_required_work_in_task() {
                // Given
                final var startedNewTransaction = new AtomicBoolean();

                // When
                transactionTemplate.executeWithoutResult(status -> afterCommitExecutor.execute(() -> transactionTemplate
                        .executeWithoutResult(inner -> startedNewTransaction.set(inner.isNewTransaction()))));

                // Then
                assertThat(startedNewTransaction).isTrue();
            }

            @Test
            @DisplayName("작업 안의 새 트랜잭션에서 다시 등록한 작업도 그 커밋 이후에 한 번 실행해야 한다")
            void it_runs_task_registered_inside_task_transaction_once() {
                // Given
                final var nestedExecutionCount = new AtomicInteger();
                final var countBeforeInnerCommit = new AtomicInteger(-1);

                // When
                transactionTemplate.executeWithoutResult(
                        status -> afterCommitExecutor.execute(() -> transactionTemplate.executeWithoutResult(inner -> {
                            afterCommitExecutor.execute(nestedExecutionCount::incrementAndGet);
                            countBeforeInnerCommit.set(nestedExecutionCount.get());
                        })));

                // Then
                assertThat(countBeforeInnerCommit).hasValue(0);
                assertThat(nestedExecutionCount).hasValue(1);
            }

            @Test
            @DisplayName("작업 안에서 트랜잭션 없이 다시 등록한 작업은 즉시 한 번 실행해야 한다")
            void it_runs_task_registered_inside_task_without_transaction_once() {
                // Given
                final var nestedExecutionCount = new AtomicInteger();

                // When
                transactionTemplate.executeWithoutResult(status -> afterCommitExecutor
                        .execute(() -> afterCommitExecutor.execute(nestedExecutionCount::incrementAndGet)));

                // Then
                assertThat(nestedExecutionCount).hasValue(1);
            }
        }

        @Nested
        @DisplayName("트랜잭션이 롤백되면")
        class Context_with_rollback {

            @Test
            @DisplayName("작업을 실행하지 않아야 한다")
            void it_does_not_run_task() {
                // Given
                final var executionCount = new AtomicInteger();

                // When
                transactionTemplate.executeWithoutResult(status -> {
                    afterCommitExecutor.execute(executionCount::incrementAndGet);
                    status.setRollbackOnly();
                });

                // Then
                assertThat(executionCount).hasValue(0);
            }
        }

        @Nested
        @DisplayName("이 컴포넌트를 거치지 않은 커밋 콜백 안에서 호출하면")
        class Context_inside_raw_after_commit_callback {

            @Test
            @DisplayName("작업을 버리지 않고 한 번 실행해야 한다")
            void it_runs_task_once_in_completion_phase() {
                // Given
                final var executionCount = new AtomicInteger();

                // When
                transactionTemplate.executeWithoutResult(status -> TransactionSynchronizationManager
                        .registerSynchronization(new TransactionSynchronization() {

                            @Override
                            public void afterCommit() {
                                afterCommitExecutor.execute(executionCount::incrementAndGet);
                            }
                        }));

                // Then
                assertThat(executionCount).hasValue(1);
            }
        }
    }
}
