package team.washer.server.v2.domain.reservation.support;

import java.time.LocalDateTime;
import java.util.Set;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;
import team.washer.server.v2.domain.smartthings.dto.response.SmartThingsDeviceStatusResDto;
import team.washer.server.v2.domain.smartthings.enums.MachineOperatingState;
import team.washer.server.v2.global.util.DateTimeUtil;

/**
 * RESERVED 예약을 RUNNING으로 전환해도 되는지 판정하는 단일 진입점.
 *
 * <p>
 * 라이프사이클 감지와 타임아웃 보정이 같은 입력에 다른 결론을 내리지 않도록 시작 판정을 이 컴포넌트로 모은다. 완료 판정은 다루지 않으며,
 * 상태를 확신할 수 없는 경우에는 IDLE로 단정하지 않는다.
 */
@Component
@Slf4j
public class ReservationStartDecisionSupport {

    private static final Set<String> WASHER_ACTIVE_JOB_STATES = Set.of("wash", "rinse", "spin");
    private static final Set<String> DRYER_ACTIVE_JOB_STATES = Set.of("drying", "cooling");

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
        if (status.isSwitchOff()) {
            return StartDecision.IDLE;
        }

        var operatingState = status.getOperatingState(isWasher);
        if (operatingState.isOperating()) {
            log.debug("reservation start detected by machineState isWasher={} machineState={}",
                    isWasher,
                    operatingState);
            return StartDecision.STARTED;
        }
        if (operatingState == MachineOperatingState.STOP) {
            return StartDecision.IDLE;
        }

        var jobState = status.getJobState(isWasher);
        if (isActiveJobState(jobState, isWasher)) {
            log.debug("reservation start detected by jobState isWasher={} jobState={}", isWasher, jobState);
            return StartDecision.STARTED;
        }

        var completionTime = parseCompletionTime(status.getCompletionTime(isWasher));
        if (completionTime != null && completionTime.isAfter(DateTimeUtil.nowInKorea())) {
            log.debug("reservation start detected by completionTime isWasher={} completionTime={}",
                    isWasher,
                    completionTime);
            return StartDecision.STARTED;
        }
        return StartDecision.UNKNOWN;
    }

    public boolean isStarted(SmartThingsDeviceStatusResDto status, boolean isWasher) {
        return decide(status, isWasher) == StartDecision.STARTED;
    }

    private boolean isActiveJobState(String jobState, boolean isWasher) {
        if (jobState == null || jobState.isBlank()) {
            return false;
        }
        var activeJobStates = isWasher ? WASHER_ACTIVE_JOB_STATES : DRYER_ACTIVE_JOB_STATES;
        return activeJobStates.stream().anyMatch(jobState::equalsIgnoreCase);
    }

    private LocalDateTime parseCompletionTime(String completionTimeStr) {
        return (completionTimeStr != null && !completionTimeStr.isBlank())
                ? DateTimeUtil.parseAndConvertToKoreaTime(completionTimeStr)
                : null;
    }
}
