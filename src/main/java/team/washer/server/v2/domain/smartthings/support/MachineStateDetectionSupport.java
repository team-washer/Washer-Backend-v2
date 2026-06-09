package team.washer.server.v2.domain.smartthings.support;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import team.washer.server.v2.domain.smartthings.dto.response.SmartThingsDeviceStatusResDto;
import team.washer.server.v2.global.util.DateTimeUtil;

/**
 * SmartThings 기기 상태 감지를 담당하는 지원 컴포넌트.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MachineStateDetectionSupport {

    private final DeviceStatusQuerySupport deviceStatusQuerySupport;

    /**
     * 기기가 작동 중인지 감지한다.
     */
    public boolean isRunning(SmartThingsDeviceStatusResDto status) {
        var washerOperatingState = status.getWasherOperatingState();
        var dryerOperatingState = status.getDryerOperatingState();
        var isRunning = "run".equalsIgnoreCase(washerOperatingState) || "run".equalsIgnoreCase(dryerOperatingState);
        if (isRunning) {
            log.debug("device is running");
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
     * 확인한다. 세탁기/건조기는 기기 타입별로 독립 판정한다.
     */
    public Optional<LocalDateTime> isCompleted(SmartThingsDeviceStatusResDto status) {
        if (status == null) {
            return Optional.empty();
        }
        var now = LocalDateTime.now();

        var washerCompletion = evaluateCompletion(status.getWasherJobState(),
                "finish",
                status.getWasherOperatingState(),
                status.getWasherCompletionTime(),
                now);
        if (washerCompletion.isPresent()) {
            return washerCompletion;
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
     */
    public boolean isInterrupted(SmartThingsDeviceStatusResDto status) {
        var switchStatus = status.getSwitchStatus();
        if ("off".equalsIgnoreCase(switchStatus)) {
            log.debug("기기 전원이 꺼져 있음 (switch=off)");
            return true;
        }

        var washerMachineState = status.getWasherOperatingState();
        var washerJobState = status.getWasherJobState();
        if ("stop".equalsIgnoreCase(washerMachineState) && !"finish".equalsIgnoreCase(washerJobState)) {
            log.debug("기기 세탁기가 비정상 정지됨 (machineState=stop, jobState={})", washerJobState);
            return true;
        }

        var dryerMachineState = status.getDryerOperatingState();
        var dryerJobState = status.getDryerJobState();
        if ("stop".equalsIgnoreCase(dryerMachineState) && !"finished".equalsIgnoreCase(dryerJobState)) {
            log.debug("기기 건조기가 비정상 정지됨 (machineState=stop, jobState={})", dryerJobState);
            return true;
        }

        return false;
    }

    /**
     * 기기가 일시정지 상태인지 감지한다.
     */
    public boolean isPaused(SmartThingsDeviceStatusResDto status) {
        var washerMachineState = status.getWasherOperatingState();
        if ("pause".equalsIgnoreCase(washerMachineState)) {
            log.debug("기기 세탁기가 일시정지 상태 (machineState=pause)");
            return true;
        }

        var dryerMachineState = status.getDryerOperatingState();
        if ("pause".equalsIgnoreCase(dryerMachineState)) {
            log.debug("기기 건조기가 일시정지 상태 (machineState=pause)");
            return true;
        }

        return false;
    }

    /**
     * 기기가 작동 중인지 감지한다.
     */
    @Transactional(readOnly = true)
    public boolean isRunning(String deviceId) {
        try {
            return isRunning(deviceStatusQuerySupport.queryDeviceStatus(deviceId));
        } catch (Exception e) {
            log.warn("failed to detect running state for device {}", deviceId, e);
            return false;
        }
    }

    /**
     * 기기 작업이 완료되었는지 감지한다.
     */
    @Transactional(readOnly = true)
    public Optional<LocalDateTime> isCompleted(String deviceId) {
        try {
            return isCompleted(deviceStatusQuerySupport.queryDeviceStatus(deviceId));
        } catch (Exception e) {
            log.warn("Failed to detect completion state for device: {}", deviceId, e);
            return Optional.empty();
        }
    }

    /**
     * 기기가 비정상 중단되었는지 감지한다.
     */
    @Transactional(readOnly = true)
    public boolean isInterrupted(String deviceId) {
        try {
            return isInterrupted(deviceStatusQuerySupport.queryDeviceStatus(deviceId));
        } catch (Exception e) {
            log.warn("기기 {} 중단 상태 감지 실패", deviceId, e);
            return false;
        }
    }

    /**
     * 기기가 일시정지 상태인지 감지한다.
     */
    @Transactional(readOnly = true)
    public boolean isPaused(String deviceId) {
        try {
            return isPaused(deviceStatusQuerySupport.queryDeviceStatus(deviceId));
        } catch (Exception e) {
            log.warn("기기 {} 일시정지 상태 감지 실패", deviceId, e);
            return false;
        }
    }
}
