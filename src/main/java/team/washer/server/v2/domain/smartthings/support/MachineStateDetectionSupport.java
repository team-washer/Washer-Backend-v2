package team.washer.server.v2.domain.smartthings.support;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;
import team.washer.server.v2.domain.smartthings.dto.response.SmartThingsDeviceStatusResDto;
import team.washer.server.v2.domain.smartthings.enums.MachineOperatingState;
import team.washer.server.v2.global.util.DateTimeUtil;

/**
 * SmartThings 기기 상태 감지를 담당하는 지원 컴포넌트.
 *
 * <p>
 * 모든 판정은 기기 타입(세탁기/건조기)에 해당하는 capability만 검사한다. 하나의 deviceId가 세탁기·건조기
 * capability를 동시에 노출하는 경우, 사용하지 않는 쪽의 유휴 상태를 비정상 중단으로 오판하지 않기 위함이다.
 *
 * <p>
 * 세탁기/건조기 분기와 원시 문자열 해석은 {@link SmartThingsDeviceStatusResDto}의 타입 인자 접근자
 * ({@code getOperatingState(isWasher)} 등)가 담당한다. 이 컴포넌트는 그 위에서 "작동 중·완료·중단" 같은
 * 판정만 수행한다.
 */
@Component
@Slf4j
public class MachineStateDetectionSupport {

    /**
     * 기기가 작동 중인지 감지한다. 세탁기는 washerOperatingState, 건조기는 dryerOperatingState의
     * machineState가 run인지 확인한다.
     */
    public boolean isRunning(SmartThingsDeviceStatusResDto status, boolean isWasher) {
        if (status == null) {
            return false;
        }
        var isRunning = status.getOperatingState(isWasher) == MachineOperatingState.RUN;
        if (isRunning) {
            log.debug("device is running isWasher={}", isWasher);
        }
        return isRunning;
    }

    /**
     * 기기가 물리적으로 정지 상태인지 감지한다.
     */
    public boolean isStopped(SmartThingsDeviceStatusResDto status, boolean isWasher) {
        return status != null && status.getOperatingState(isWasher) == MachineOperatingState.STOP;
    }

    /**
     * 기기가 보고한 완료 예정 시각을 한국 시간으로 변환한다. 값이 없거나 파싱할 수 없으면 빈 값을 반환한다.
     */
    public Optional<LocalDateTime> resolveCompletionTime(SmartThingsDeviceStatusResDto status, boolean isWasher) {
        if (status == null) {
            return Optional.empty();
        }
        var completionTimeStr = status.getCompletionTime(isWasher);
        if (completionTimeStr == null || completionTimeStr.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(DateTimeUtil.parseAndConvertToKoreaTime(completionTimeStr));
    }

    /**
     * 기기 작업이 완료되었는지 감지하고, 완료된 경우 완료 시각을 반환한다.
     *
     * <p>
     * SmartThings 기기는 메인 사이클이 끝나면 냉각(cooling)·구김방지(wrinklePrevent) 등 잔여 단계가 남아 있어도
     * jobState를 finish/finished로 먼저 보고하는 경우가 있다. 반대로 완료 직후 jobState가 none/null로 빠르게
     * 리셋될 수 있으므로, machineState=stop(물리적 정지)와 completionTime이 현재 시각을 지났는지(잔여시간 0)를
     * 함께 확인한다.
     */
    public Optional<LocalDateTime> isCompleted(SmartThingsDeviceStatusResDto status, boolean isWasher) {
        if (status == null) {
            return Optional.empty();
        }
        var now = DateTimeUtil.nowInKorea();
        if (!isStopped(status, isWasher)) {
            if (status.isJobStateFinished(isWasher)) {
                log.debug("job finished but machine not stopped yet machineState={} jobState={}",
                        status.getOperatingState(isWasher),
                        status.getJobState(isWasher));
            }
            return Optional.empty();
        }

        var completionTime = resolveCompletionTime(status, isWasher).orElse(null);
        if (status.isJobStateFinished(isWasher)) {
            log.debug("device job is completed jobState={} completionTime={}",
                    status.getJobState(isWasher),
                    completionTime);
            return Optional.of(completionTime != null && !completionTime.isAfter(now) ? completionTime : now);
        }

        if (completionTime != null && completionTime.isAfter(now)) {
            log.debug("device stopped but completion time still in future completionTime={} jobState={}",
                    completionTime,
                    status.getJobState(isWasher));
            return Optional.empty();
        }

        if (status.isJobStateReset(isWasher) && completionTime != null) {
            log.debug("device job is completed after job reset jobState={} completionTime={}",
                    status.getJobState(isWasher),
                    completionTime);
            return Optional.of(completionTime);
        }

        return Optional.empty();
    }

    /**
     * 기기 전원이 꺼졌는지 감지한다.
     *
     * <p>
     * 전원 차단은 사이클 진행 여부와 무관하게 명백한 중단이므로, 완료 예정 시각 근처의 정지를 보류하는 판정보다 먼저 평가되어야 한다. 그렇지
     * 않으면 완료 예정 시각이 유예 범위 안에 있는 동안 중단이 영영 확정되지 않는다.
     */
    public boolean isPoweredOff(SmartThingsDeviceStatusResDto status) {
        if (status == null) {
            return false;
        }
        var isPoweredOff = status.isSwitchOff();
        if (isPoweredOff) {
            log.debug("device power off detected switch=off");
        }
        return isPoweredOff;
    }

    /**
     * 기기가 비정상 중단되었는지 감지한다.
     *
     * <p>
     * 전원이 꺼진 경우(switch=off)는 명백한 중단으로 본다. 그 외에는 사이클 진행 단계(wash/rinse/spin/drying 등)
     * 도중 machineState가 stop으로 보고된 경우에만 중단으로 판정한다. machineState=stop이면서 jobState가
     * finish/finished(정상 완료)이거나 none/공백(사이클 종료 직후 리셋·유휴)인 경우는, 정상 완료를 비정상 중단으로 오판하지
     * 않도록 중단으로 보지 않는다.
     */
    public boolean isInterrupted(SmartThingsDeviceStatusResDto status, boolean isWasher) {
        if (status == null) {
            return false;
        }
        if (isPoweredOff(status)) {
            return true;
        }

        if (status.getOperatingState(isWasher) != MachineOperatingState.STOP) {
            return false;
        }
        if (status.isJobStateFinished(isWasher)) {
            return false;
        }
        if (status.isJobStateReset(isWasher)) {
            log.debug("machine stopped with idle jobState, not treated as interruption jobState={}",
                    status.getJobState(isWasher));
            return false;
        }

        log.debug("machine interrupted mid-cycle machineState=stop jobState={}", status.getJobState(isWasher));
        return true;
    }

    /**
     * 기기가 일시정지 상태인지 감지한다.
     */
    public boolean isPaused(SmartThingsDeviceStatusResDto status, boolean isWasher) {
        if (status == null) {
            return false;
        }
        var isPaused = status.getOperatingState(isWasher) == MachineOperatingState.PAUSE;
        if (isPaused) {
            log.debug("device is paused isWasher={}", isWasher);
        }
        return isPaused;
    }
}
