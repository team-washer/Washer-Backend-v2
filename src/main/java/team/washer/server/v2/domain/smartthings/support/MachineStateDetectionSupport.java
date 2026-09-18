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
     * 기기 작업이 완료되었는지 감지한다.
     *
     * <p>
     * 아래 중 하나면 완료로 본다.
     * <ul>
     * <li>jobState가 finish(세탁기)/finished(건조기). machineState는 보지 않는다. 건조기는 finished
     * 이후에도 구김방지(wrinklePrevent) 단계로 machineState=run을 유지할 수 있고, 완료 시 전원을 차단해 이 단계를
     * 끊는다.</li>
     * <li>machineState=stop이고 jobState가 none/공백. 폴링 사이에 완료 신호가 지나가고 jobState가 리셋된
     * 경우다. 전원이 꺼진 정지는 사이클 중단일 수 있으므로 완료로 보지 않는다.</li>
     * </ul>
     *
     * <p>
     * 완료 신호가 이번 예약의 사이클에서 온 것인지는 호출 측이 {@link #resolveCompletionSignalTimestamp}로
     * 확인한다.
     */
    public boolean isCompleted(SmartThingsDeviceStatusResDto status, boolean isWasher) {
        if (status == null) {
            return false;
        }
        if (status.isJobStateFinished(isWasher)) {
            log.debug("device job finished machineState={} jobState={}",
                    status.getOperatingState(isWasher),
                    status.getJobState(isWasher));
            return true;
        }
        if (isStopped(status, isWasher) && status.isJobStateReset(isWasher) && !isPoweredOff(status)) {
            log.debug("device stopped with idle jobState jobState={}", status.getJobState(isWasher));
            return true;
        }
        return false;
    }

    /**
     * 완료 판정의 근거가 된 상태 값의 갱신 시각을 한국 시간으로 반환한다. jobState 완료 신호면 jobState 갱신 시각, 정지
     * 신호면 machineState 갱신 시각이다. 값이 없거나 파싱할 수 없으면 빈 값을 반환한다.
     */
    public Optional<LocalDateTime> resolveCompletionSignalTimestamp(SmartThingsDeviceStatusResDto status,
            boolean isWasher) {
        if (status == null) {
            return Optional.empty();
        }
        var timestamp = status.isJobStateFinished(isWasher)
                ? status.getJobStateTimestamp(isWasher)
                : status.getOperatingStateTimestamp(isWasher);
        if (timestamp == null || timestamp.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(DateTimeUtil.parseAndConvertToKoreaTime(timestamp));
    }

    /**
     * 기기 전원이 꺼졌는지 감지한다.
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

        if (!isStopped(status, isWasher)) {
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
