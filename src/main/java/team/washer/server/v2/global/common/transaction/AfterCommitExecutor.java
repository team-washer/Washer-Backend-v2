package team.washer.server.v2.global.common.transaction;

import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import lombok.extern.slf4j.Slf4j;

/**
 * 트랜잭션 커밋 이후에 실행할 부수 효과의 공용 경계.
 *
 * <p>
 * 커밋 콜백은 이미 커밋된 트랜잭션의 자원이 스레드에 묶인 채로 실행된다. 그 상태에서 {@code REQUIRED} 메서드를 호출하면 끝난
 * 트랜잭션에 참여하므로 저장이 커밋된다는 보장이 없고, 그 안에서 다시 등록한 커밋 콜백도 호출되지 않는다. 이 컴포넌트는 작업을 기존
 * 트랜잭션을 중단한 상태에서 실행해, 작업 안의 트랜잭션 메서드가 전파 속성과 무관하게 새 트랜잭션으로 시작하고 정상 커밋되게 한다.
 * </p>
 */
@Slf4j
@Component
public class AfterCommitExecutor {

    private final TransactionTemplate suspendingTemplate;

    public AfterCommitExecutor(final PlatformTransactionManager transactionManager) {
        this.suspendingTemplate = new TransactionTemplate(transactionManager);
        this.suspendingTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_NOT_SUPPORTED);
    }

    /**
     * 현재 트랜잭션이 커밋된 뒤 작업을 실행한다. 롤백되면 실행하지 않고, 트랜잭션 밖에서는 즉시 실행한다.
     *
     * <p>
     * 작업에서 던진 예외는 삼키지 않는다. 커밋 결과에 영향을 주지 않아야 하는 작업은 호출자가 직접 예외를 처리한다.
     * </p>
     *
     * @param task
     *            커밋 이후 실행할 작업
     */
    public void execute(final Runnable task) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive()) {
            task.run();
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {

            private boolean executed;

            @Override
            public void afterCommit() {
                executed = true;
                runOutsideTransaction(task);
            }

            @Override
            public void afterCompletion(final int status) {
                // 이 컴포넌트를 거치지 않은 커밋 콜백 안에서 등록되면 afterCommit이 호출되지 않으므로 완료 단계에서 보완 실행한다
                if (status == STATUS_COMMITTED && !executed) {
                    log.warn("after-commit task registered during commit completion running in completion phase");
                    runOutsideTransaction(task);
                }
            }
        });
    }

    private void runOutsideTransaction(final Runnable task) {
        suspendingTemplate.executeWithoutResult(status -> task.run());
    }
}
