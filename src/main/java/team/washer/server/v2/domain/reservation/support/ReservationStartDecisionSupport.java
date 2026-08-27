package team.washer.server.v2.domain.reservation.support;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import team.washer.server.v2.domain.smartthings.dto.response.SmartThingsDeviceStatusResDto;
import team.washer.server.v2.domain.smartthings.support.MachineStateDetectionSupport;
import team.washer.server.v2.global.util.DateTimeUtil;

/**
 * RESERVED 예약을 RUNNING으로 전환해도 되는지 판정하는 단일 진입점.
 *
 * <p>
 * 라이프사이클 감지와 타임아웃 보정이 같은 입력에 다른 결론을 내리지 않도록 시작 판정을 이 컴포넌트로 모은다. 기기 상태의 원시 해석은
 * {@link MachineStateDetectionSupport}와 {@link SmartThingsDeviceStatusResDto}에
 * 위임하고, 이 컴포넌트는 예약 시작 여부만 판단한다. 완료 판정은 다루지 않으며, 상태를 확신할 수 없는 경우에는 IDLE로 단정하지
 * 않는다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ReservationStartDecisionSupport {

    private final MachineStateDetectionSupport machineStateDetectionSupport;

    public enum StartDecision {
        STARTED, IDLE, UNKNOWN
    }

    /**
     * SmartThings 상태를 바탕으로 예약 시작 여부를 판정한다.
     */
    public StartDecision decide(SmartThingsDeviceStatusResDto status, boolean isWasher) {
        if (status == null) {
            return StartDecision.UNKNOWN;
        }
        if (machineStateDetectionSupport.isPoweredOff(status)) {
            return StartDecision.IDLE;
        }
        if (machineStateDetectionSupport.isRunning(status, isWasher)) {
            log.debug("reservation start detected by running machineState isWasher={}", isWasher);
            return StartDecision.STARTED;
        }
        if (machineStateDetectionSupport.isPaused(status, isWasher)) {
            log.debug("reservation start detected by paused machineState isWasher={}", isWasher);
            return StartDecision.STARTED;
        }
        if (machineStateDetectionSupport.isStopped(status, isWasher)) {
            return StartDecision.IDLE;
        }
        if (status.isJobStateActive(isWasher)) {
            log.debug("reservation start detected by jobState isWasher={} jobState={}",
                    isWasher,
                    status.getJobState(isWasher));
            return StartDecision.STARTED;
        }

        return machineStateDetectionSupport.resolveCompletionTime(status, isWasher)
                .filter(completionTime -> completionTime.isAfter(DateTimeUtil.nowInKorea())).map(completionTime -> {
                    log.debug("reservation start detected by completionTime isWasher={} completionTime={}",
                            isWasher,
                            completionTime);
                    return StartDecision.STARTED;
                }).orElse(StartDecision.UNKNOWN);
    }

    /**
     * 예약이 시작된 것으로 확정할 수 있는지 반환한다.
     */
    public boolean isStarted(SmartThingsDeviceStatusResDto status, boolean isWasher) {
        return decide(status, isWasher) == StartDecision.STARTED;
    }
}
