package team.washer.server.v2.domain.reservation.support;

import java.time.LocalDateTime;

/**
 * RUNNING 예약에 대한 사이클 완료 판정 결과.
 *
 * @param outcome
 *            판정 결과
 * @param completionTime
 *            완료 후보 시각. {@link Outcome#NOT_COMPLETED}이면 {@code null}
 * @param reason
 *            판정 근거. 로그의 {@code reason} 필드로 그대로 사용된다
 */
public record CompletionDecision(Outcome outcome, LocalDateTime completionTime, String reason) {

    /**
     * 완료 판정 결과의 종류.
     */
    public enum Outcome {
        /** 완료 후보가 모든 가드를 통과했다. */
        COMPLETED,
        /** 완료 후보는 있으나 가드에 걸려 판정을 보류한다. */
        DEFERRED,
        /** 완료 후보 자체가 없다. */
        NOT_COMPLETED
    }

    public static CompletionDecision completed(LocalDateTime completionTime, String reason) {
        return new CompletionDecision(Outcome.COMPLETED, completionTime, reason);
    }

    public static CompletionDecision deferred(LocalDateTime completionTime, String reason) {
        return new CompletionDecision(Outcome.DEFERRED, completionTime, reason);
    }

    public static CompletionDecision notCompleted() {
        return new CompletionDecision(Outcome.NOT_COMPLETED, null, "not_completed");
    }

    public boolean isCompleted() {
        return this.outcome == Outcome.COMPLETED;
    }

    public boolean isDeferred() {
        return this.outcome == Outcome.DEFERRED;
    }
}
