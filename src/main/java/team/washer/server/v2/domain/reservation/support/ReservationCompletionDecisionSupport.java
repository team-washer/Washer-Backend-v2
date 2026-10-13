package team.washer.server.v2.domain.reservation.support;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import team.washer.server.v2.domain.reservation.entity.Reservation;
import team.washer.server.v2.domain.smartthings.dto.response.SmartThingsDeviceStatusResDto;
import team.washer.server.v2.domain.smartthings.support.MachineStateDetectionSupport;
import team.washer.server.v2.global.common.constants.ReservationConstants;
import team.washer.server.v2.global.util.DateTimeUtil;

/**
 * 기기 사이클의 완료 여부를 판정하는 단일 진입점.
 *
 * <p>
 * 기기가 완료 신호를 보고하면({@link MachineStateDetectionSupport#isCompleted}) 즉시 완료로
 * 판정한다. 완료 예정 시각·연속 감지 같은 보조 조건은 두지 않는다. 완료가 실제 종료보다 조금 일찍 표시되는 것은 감수하고, 완료 시
 * 기기 전원을 차단해 남은 단계(냉각·구김방지 등)가 기기를 점유하지 않게 한다.
 *
 * <p>
 * 완료 신호에 대한 가드는 이전 사이클 신호 검사 하나다. 완료 신호의 갱신 시각이 이번 예약의 시작 시각보다 앞서면 이전 사이클의 잔재로
 * 보고 판정을 보류한다({@code stale_completion}).
 *
 * <p>
 * 완료 신호 없이 전원이 꺼진 유휴 정지({@link MachineStateDetectionSupport#isPoweredOffIdle})는
 * 폴링 사이에 완료 신호를 놓친 정상 종료일 수도, 사이클 도중 전원을 끈 중단일 수도 있다. 이때만 예약의 완료 예정 시각을 근거로 쓴다.
 * 기기가 멈춘 시각이 완료 예정 시각 무렵 이후면 완료로
 * 판정하고({@code powered_off_after_expected_completion}), 그보다 일찍 멈췄거나 일시정지됐거나 대조할
 * 근거가 없으면 완료로 보지 않고 기각 사유만 남겨 기존 중단 판정에 맡긴다.
 *
 * <p>
 * 이 컴포넌트는 판정만 하고 상태를 바꾸지 않는다. DB 갱신은 호출
 * 측({@code ReservationLifecycleProcessor})의 책임이다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ReservationCompletionDecisionSupport {

    private static final String REASON_JOB_FINISHED = "job_finished";
    private static final String REASON_STOPPED_IDLE = "stopped_idle";
    private static final String REASON_STALE_COMPLETION = "stale_completion";
    private static final String REASON_POWERED_OFF_AFTER_EXPECTED_COMPLETION = "powered_off_after_expected_completion";
    private static final String REASON_POWER_OFF_BEFORE_EXPECTED_COMPLETION = "power_off_before_expected_completion";
    private static final String REASON_PAUSED_BEFORE_EXPECTED_COMPLETION = "paused_before_expected_completion";
    private static final String REASON_POWER_OFF_WITHOUT_COMPLETION_EVIDENCE = "power_off_without_completion_evidence";

    private final MachineStateDetectionSupport machineStateDetectionSupport;

    /**
     * 예약과 기기 상태를 바탕으로 사이클 완료 여부를 판정한다.
     *
     * @param reservation
     *            판정 대상 RUNNING 예약
     * @param status
     *            SmartThings에서 조회한 기기 상태
     * @param isWasher
     *            세탁기 여부
     * @return 완료 판정 결과
     */
    public CompletionDecision decide(Reservation reservation, SmartThingsDeviceStatusResDto status, boolean isWasher) {
        if (!machineStateDetectionSupport.isCompleted(status, isWasher)) {
            return decidePoweredOffIdle(reservation, status, isWasher);
        }

        var now = DateTimeUtil.nowInKorea();
        var signalTimestamp = machineStateDetectionSupport.resolveCompletionSignalTimestamp(status, isWasher)
                .orElse(null);
        var startTime = reservation.getStartTime();
        if (signalTimestamp != null && startTime != null && signalTimestamp.isBefore(startTime)) {
            return CompletionDecision.deferred(signalTimestamp, REASON_STALE_COMPLETION);
        }

        var reason = status.isJobStateFinished(isWasher) ? REASON_JOB_FINISHED : REASON_STOPPED_IDLE;
        return CompletionDecision.completed(now, reason);
    }

    /**
     * 완료 신호 없이 전원이 꺼진 유휴 정지를 판정한다.
     *
     * <p>
     * machineState 갱신 시각을 기기가 멈춘 시각으로 보고, 진행 중에 저장해 둔 예약의 완료 예정 시각과 대조한다. 현재 시각이
     * 아니라 멈춘 시각을 쓰므로, 조회 실패 등으로 감지가 늦어져도 일찍 꺼진 사이클이 완료로 바뀌지 않는다. 이 갱신 시각은 기기가 상태를
     * 마지막으로 보고한 시각이어서, 전원이 꺼질 때 상태를 다시 보고하는 기종에서는 멈춘 시각이 아니라 전원이 꺼진 시각이 된다.
     *
     * <p>
     * 전원이 꺼질 때 기기는 completionTime을 꺼진 시각에 기본 코스 시간을 더한 값으로 다시 보고한다. 방금 끝난 사이클의 완료
     * 예정 시각이 아니므로 꺼진 상태의 completionTime은 쓰지 않는다.
     *
     * <p>
     * 일시정지된 채로 전원이 꺼지면 machineState는 전원이 꺼질 때에야 stop으로 바뀌므로, 멈춘 시각만 보면 일시정지로 사이클이 서
     * 있던 시간까지 진행한 것으로 계산된다. 예약에 일시정지 시작 시각이 남아 있으면 그 시각도 완료 예정 시각 무렵 이후여야 완료로 본다.
     * 일시정지 시작 시각은 재개가 관측되면 지워지므로, 재개 후 끝까지 돌린 사이클에는 영향을 주지 않는다.
     */
    private CompletionDecision decidePoweredOffIdle(Reservation reservation,
            SmartThingsDeviceStatusResDto status,
            boolean isWasher) {
        if (!machineStateDetectionSupport.isPoweredOffIdle(status, isWasher)) {
            return CompletionDecision.notCompleted();
        }

        var stoppedAt = machineStateDetectionSupport.resolveCompletionSignalTimestamp(status, isWasher).orElse(null);
        var startTime = reservation.getStartTime();
        var expectedCompletionTime = reservation.getExpectedCompletionTime();
        if (stoppedAt == null || startTime == null || expectedCompletionTime == null || stoppedAt.isBefore(startTime)) {
            return CompletionDecision.notCompleted(REASON_POWER_OFF_WITHOUT_COMPLETION_EVIDENCE);
        }

        var earliestCompletion = expectedCompletionTime
                .minusMinutes(ReservationConstants.POWER_OFF_COMPLETION_TOLERANCE_MINUTES);
        if (stoppedAt.isBefore(earliestCompletion)) {
            return CompletionDecision.notCompleted(REASON_POWER_OFF_BEFORE_EXPECTED_COMPLETION);
        }
        var pausedAt = reservation.getPausedAt();
        if (pausedAt != null && pausedAt.isBefore(earliestCompletion)) {
            return CompletionDecision.notCompleted(REASON_PAUSED_BEFORE_EXPECTED_COMPLETION);
        }
        return CompletionDecision.completed(DateTimeUtil.nowInKorea(), REASON_POWERED_OFF_AFTER_EXPECTED_COMPLETION);
    }
}
