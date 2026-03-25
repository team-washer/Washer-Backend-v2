package team.washer.server.v2.domain.smartthings.support;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    @Transactional(readOnly = true)
    public boolean isRunning(String deviceId) {
        try {
            var status = deviceStatusQuerySupport.queryDeviceStatus(deviceId);

            var washerOperatingState = status.getWasherOperatingState();
            var dryerOperatingState = status.getDryerOperatingState();

            var isRunning = "run".equalsIgnoreCase(washerOperatingState) || "run".equalsIgnoreCase(dryerOperatingState);

            if (isRunning) {
                log.debug("device {} is running", deviceId);
            }

            return isRunning;
        } catch (Exception e) {
            log.warn("failed to detect running state for device {}", deviceId, e);
            return false;
        }
    }

    /**
     * 기기 작업이 완료되었는지 감지하고, 완료된 경우 완료 시각을 반환한다.
     */
    @Transactional(readOnly = true)
    public Optional<LocalDateTime> isCompleted(String deviceId) {
        try {
            var status = deviceStatusQuerySupport.queryDeviceStatus(deviceId);

            var washerJobState = status.getWasherJobState();
            var dryerJobState = status.getDryerJobState();

            // 세탁기: "finish", 건조기: "finished" (SmartThings 명세 기준)
            var isFinished = "finish".equalsIgnoreCase(washerJobState) || "finished".equalsIgnoreCase(dryerJobState);

            if (isFinished) {
                log.debug("Device {} job is finished", deviceId);
                return Optional.ofNullable(DateTimeUtil.parseAndConvertToKoreaTime(status.getCompletionTime()));
            }

            return Optional.empty();
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
            var status = deviceStatusQuerySupport.queryDeviceStatus(deviceId);

            // 전원이 꺼진 경우
            var switchStatus = status.getSwitchStatus();
            if ("off".equalsIgnoreCase(switchStatus)) {
                log.debug("기기 {} 전원이 꺼져 있음 (switch=off)", deviceId);
                return true;
            }

            // 완료 없이 정지된 경우: machineState="stop" 이지만 jobState가 정상 완료값이 아닌 경우
            var washerMachineState = status.getWasherOperatingState();
            var washerJobState = status.getWasherJobState();
            if ("stop".equalsIgnoreCase(washerMachineState) && !"finish".equalsIgnoreCase(washerJobState)) {
                log.debug("기기 {} 세탁기가 비정상 정지됨 (machineState=stop, jobState={})", deviceId, washerJobState);
                return true;
            }

            var dryerMachineState = status.getDryerOperatingState();
            var dryerJobState = status.getDryerJobState();
            if ("stop".equalsIgnoreCase(dryerMachineState) && !"finished".equalsIgnoreCase(dryerJobState)) {
                log.debug("기기 {} 건조기가 비정상 정지됨 (machineState=stop, jobState={})", deviceId, dryerJobState);
                return true;
            }

            return false;
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
            var status = deviceStatusQuerySupport.queryDeviceStatus(deviceId);

            var washerMachineState = status.getWasherOperatingState();
            if ("pause".equalsIgnoreCase(washerMachineState)) {
                log.debug("기기 {} 세탁기가 일시정지 상태 (machineState=pause)", deviceId);
                return true;
            }

            var dryerMachineState = status.getDryerOperatingState();
            if ("pause".equalsIgnoreCase(dryerMachineState)) {
                log.debug("기기 {} 건조기가 일시정지 상태 (machineState=pause)", deviceId);
                return true;
            }

            return false;
        } catch (Exception e) {
            log.warn("기기 {} 일시정지 상태 감지 실패", deviceId, e);
            return false;
        }
    }
}
