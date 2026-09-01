package team.washer.server.v2.domain.smartthings.support;

import java.time.LocalDateTime;

/**
 * 기기 상태 스냅샷에서 읽어낸 사이클 완료 신호.
 *
 * <p>
 * {@code machineState=stop} 이면서 jobState가 리셋된 상태는 사이클이 끝났다는 신호이지만, 기기가 보고하는 완료
 * 예정 시각이 이번 사이클의 종료 시각일 수도 있고 다음 사이클 기준의 미래 값으로 되돌아간 값일 수도 있다. 후자는 그 시각을 완료
 * 시각으로 쓸 수 없어 예약 컨텍스트(시작 시각·상태 갱신 시각)로 한 번 더 검증해야 하므로,
 * {@link Kind#JOB_RESET_WITH_FUTURE_COMPLETION}으로 구분해 호출 측에 넘긴다.
 *
 * @param kind
 *            완료 신호의 종류
 * @param completionTime
 *            기기가 보고한 완료 시각. {@link Kind#NONE}이면 {@code null}
 */
public record MachineCompletionSignal(Kind kind, LocalDateTime completionTime) {

    /**
     * 완료 신호의 종류.
     */
    public enum Kind {
        /** 완료 신호가 없다. */
        NONE,
        /** 완료가 확인됐고 {@code completionTime}을 완료 시각으로 쓸 수 있다. */
        COMPLETED,
        /** 정지·jobState 리셋으로 사이클은 끝났으나, 보고된 완료 시각이 미래라 그대로 쓸 수 없다. */
        JOB_RESET_WITH_FUTURE_COMPLETION
    }

    public static MachineCompletionSignal none() {
        return new MachineCompletionSignal(Kind.NONE, null);
    }

    public static MachineCompletionSignal completed(LocalDateTime completionTime) {
        return new MachineCompletionSignal(Kind.COMPLETED, completionTime);
    }

    public static MachineCompletionSignal jobResetWithFutureCompletion(LocalDateTime completionTime) {
        return new MachineCompletionSignal(Kind.JOB_RESET_WITH_FUTURE_COMPLETION, completionTime);
    }

    public boolean isCompleted() {
        return this.kind == Kind.COMPLETED;
    }

    public boolean isJobResetWithFutureCompletion() {
        return this.kind == Kind.JOB_RESET_WITH_FUTURE_COMPLETION;
    }
}
