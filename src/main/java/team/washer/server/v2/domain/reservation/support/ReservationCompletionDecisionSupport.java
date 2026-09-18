package team.washer.server.v2.domain.reservation.support;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import team.washer.server.v2.domain.reservation.entity.Reservation;
import team.washer.server.v2.domain.smartthings.dto.response.SmartThingsDeviceStatusResDto;
import team.washer.server.v2.domain.smartthings.support.MachineStateDetectionSupport;
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
 * 유일한 가드는 이전 사이클 신호 검사다. 완료 신호의 갱신 시각이 이번 예약의 시작 시각보다 앞서면 이전 사이클의 잔재로 보고 판정을
 * 보류한다({@code stale_completion}).
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
            return CompletionDecision.notCompleted();
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
}
