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
 * 폴링 사이에 완료 신호를 놓친 정상 종료일 수도, 사이클 도중 전원을 끈 중단일 수도 있다. 둘을 구분할 운영 상태 sequence가
 * 확보되기 전까지는 완료로 확정하지 않는다. 예약의 완료 예정 시각·일시정지 이력과 대조한 분류를 기각 사유로만 담아 기존 중단 판정에
 * 맡기고, 호출 측이 이를 관측 로그로 남긴다.
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
    private static final String REASON_POWER_OFF_NEAR_EXPECTED_COMPLETION = "power_off_near_expected_completion";
    private static final String REASON_POWER_OFF_BEFORE_EXPECTED_COMPLETION = "power_off_before_expected_completion";
    private static final String REASON_POWER_OFF_WHILE_PAUSED = "power_off_while_paused";
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
            return classifyPoweredOffIdle(reservation, status, isWasher);
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
     * 완료 신호 없이 전원이 꺼진 유휴 정지를 운영 관측용으로 분류한다. 어떤 분류든 완료로 확정하지 않는다.
     *
     * <p>
     * machineState 갱신 시각을 기기가 멈춘 시각으로 보고 예약의 완료 예정 시각과 대조한다. 이 갱신 시각은 기기가 상태를 마지막으로
     * 보고한 시각이어서, 전원이 꺼질 때 상태를 다시 보고하는 기종에서는 전원이 꺼진 시각이 된다. 전원이 꺼질 때 기기는
     * completionTime을 꺼진 시각에 기본 코스 시간을 더한 값으로 다시 보고하므로 꺼진 상태의 completionTime은 쓰지
     * 않는다.
     *
     * <p>
     * 재개가 관측되지 않은 일시정지 이력이 있으면 시각과 관계없이 일시정지 중 전원 꺼짐으로 분류한다. 일시정지 시작 시각은 서버가 처음 관측한
     * 시각이라 조회가 끊긴 동안의 실제 일시정지 시각을 알 수 없기 때문이다.
     */
    private CompletionDecision classifyPoweredOffIdle(Reservation reservation,
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

        var nearCompletionFrom = expectedCompletionTime
                .minusMinutes(ReservationConstants.POWER_OFF_NEAR_COMPLETION_WINDOW_MINUTES);
        if (stoppedAt.isBefore(nearCompletionFrom)) {
            return CompletionDecision.notCompleted(REASON_POWER_OFF_BEFORE_EXPECTED_COMPLETION);
        }
        if (reservation.getPausedAt() != null) {
            return CompletionDecision.notCompleted(REASON_POWER_OFF_WHILE_PAUSED);
        }
        return CompletionDecision.notCompleted(REASON_POWER_OFF_NEAR_EXPECTED_COMPLETION);
    }
}
