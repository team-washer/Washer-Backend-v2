package team.washer.server.v2.domain.smartthings.support;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;
import team.washer.server.v2.domain.smartthings.dto.response.SmartThingsDeviceStatusResDto;
import team.washer.server.v2.global.util.DateTimeUtil;

/**
 * SmartThings 기기 상태 감지를 담당하는 지원 컴포넌트.
 *
 * <p>
 * 모든 판정은 기기 타입(세탁기/건조기)에 해당하는 capability만 검사한다. 하나의 deviceId가 세탁기·건조기
 * capability를 동시에 노출하는 경우, 사용하지 않는 쪽의 유휴 상태를 비정상 중단으로 오판하지 않기 위함이다.
 */
@Component
@Slf4j
public class MachineStateDetectionSupport {

    /**
     * completionTime은 항상 한국 시각(Asia/Seoul)으로 변환되므로, 비교 기준 시각도 시스템 타임존과 무관하게 한국 시각으로
     * 고정한다.
     */
    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");

    /**
     * 기기가 작동 중인지 감지한다. 세탁기는 washerOperatingState, 건조기는 dryerOperatingState의
     * machineState가 run인지 확인한다.
     */
    public boolean isRunning(SmartThingsDeviceStatusResDto status, boolean isWasher) {
        if (status == null) {
            return false;
        }
        var machineState = isWasher ? status.getWasherOperatingState() : status.getDryerOperatingState();
        var isRunning = "run".equalsIgnoreCase(machineState);
        if (isRunning) {
            log.debug("device is running isWasher={}", isWasher);
        }
        return isRunning;
    }

    /**
     * 기기 작업이 완료되었는지 감지하고, 완료된 경우 완료 시각을 반환한다.
     *
     * <p>
     * SmartThings 기기는 메인 사이클이 끝나면 냉각(cooling)·구김방지(wrinklePrevent) 등 잔여 단계가 남아 있어도
     * jobState를 finish/finished로 먼저 보고하는 경우가 있다. jobState만으로 판정하면 실제 종료 2~4분 전에 완료로
     * 오인하므로, machineState=stop(물리적 정지)와 completionTime이 현재 시각을 지났는지(잔여시간 0)를 함께
     * 확인한다.
     */
    public Optional<LocalDateTime> isCompleted(SmartThingsDeviceStatusResDto status, boolean isWasher) {
        if (status == null) {
            return Optional.empty();
        }
        var now = LocalDateTime.now(KOREA_ZONE);
        if (isWasher) {
            return evaluateCompletion(status.getWasherJobState(),
                    "finish",
                    status.getWasherOperatingState(),
                    status.getWasherCompletionTime(),
                    now);
        }
        return evaluateCompletion(status
                .getDryerJobState(), "finished", status.getDryerOperatingState(), status.getDryerCompletionTime(), now);
    }

    /**
     * jobState·machineState·completionTime을 종합하여 단일 기기 타입의 완료 여부를 판정한다. 세 조건을 모두
     * 만족할 때만 완료로 보고, 완료 시각(없으면 현재 시각)을 반환한다.
     */
    private Optional<LocalDateTime> evaluateCompletion(String jobState,
            String finishedJobState,
            String machineState,
            String completionTimeStr,
            LocalDateTime now) {
        if (!finishedJobState.equalsIgnoreCase(jobState)) {
            return Optional.empty();
        }
        if (!"stop".equalsIgnoreCase(machineState)) {
            log.debug("job finished but machine not stopped yet machineState={} jobState={}", machineState, jobState);
            return Optional.empty();
        }
        var completionTime = (completionTimeStr != null && !completionTimeStr.isBlank())
                ? DateTimeUtil.parseAndConvertToKoreaTime(completionTimeStr)
                : null;
        if (completionTime != null && completionTime.isAfter(now)) {
            log.debug("job finished but completion time still in future completionTime={} jobState={}",
                    completionTime,
                    jobState);
            return Optional.empty();
        }
        log.debug("device job is completed jobState={} completionTime={}", jobState, completionTime);
        return Optional.of(completionTime != null ? completionTime : now);
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
        if ("off".equalsIgnoreCase(status.getSwitchStatus())) {
            log.debug("device power off detected switch=off");
            return true;
        }

        var machineState = isWasher ? status.getWasherOperatingState() : status.getDryerOperatingState();
        if (!"stop".equalsIgnoreCase(machineState)) {
            return false;
        }

        var jobState = isWasher ? status.getWasherJobState() : status.getDryerJobState();
        var finishedJobState = isWasher ? "finish" : "finished";
        if (finishedJobState.equalsIgnoreCase(jobState)) {
            return false;
        }
        if (jobState == null || jobState.isBlank() || "none".equalsIgnoreCase(jobState)) {
            log.debug("machine stopped with idle jobState, not treated as interruption jobState={}", jobState);
            return false;
        }

        log.debug("machine interrupted mid-cycle machineState=stop jobState={}", jobState);
        return true;
    }

    /**
     * 기기가 일시정지 상태인지 감지한다.
     */
    public boolean isPaused(SmartThingsDeviceStatusResDto status, boolean isWasher) {
        if (status == null) {
            return false;
        }
        var machineState = isWasher ? status.getWasherOperatingState() : status.getDryerOperatingState();
        var isPaused = "pause".equalsIgnoreCase(machineState);
        if (isPaused) {
            log.debug("device is paused isWasher={}", isWasher);
        }
        return isPaused;
    }
}
