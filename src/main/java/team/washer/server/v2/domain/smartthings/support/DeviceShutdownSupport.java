package team.washer.server.v2.domain.smartthings.support;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import team.washer.server.v2.domain.machine.entity.Machine;
import team.washer.server.v2.domain.smartthings.dto.request.SmartThingsCommandReqDto;
import team.washer.server.v2.domain.smartthings.dto.response.SmartThingsDeviceStatusResDto;
import team.washer.server.v2.domain.smartthings.service.SendDeviceCommandService;

/**
 * 기기를 상황에 맞게 안전하게 종료하는 공통 컴포넌트.
 *
 * <p>
 * 작동 중인 기기를 전원 차단(switch off)으로 끄면 물이 차 있는 상태에서 중단되어 위험하다. 따라서 작동 중인 기기는
 * setMachineState(stop)으로 사이클을 정상 종료시키고, 이미 멈춘 유휴 기기만 전원을 정리한다. 무단 사용 종료 등 모든
 * 강제 종료 상황에서 이 컴포넌트를 통해 종료한다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DeviceShutdownSupport {

    private final SendDeviceCommandService sendDeviceCommandService;

    /**
     * 기기 종료 결과.
     */
    public enum ShutdownResult {
        /** 유휴 기기의 전원을 차단함 */
        POWERED_OFF,
        /** 작동 중인 기기를 안전하게 정지시킴 */
        STOPPED,
        /** 작동 중이나 원격 제어가 꺼져 있어 종료하지 못함 */
        SKIPPED_REMOTE_DISABLED,
        /** 기기 상태를 알 수 없어 종료하지 않음 */
        SKIPPED_UNKNOWN
    }

    /**
     * 기기 상태에 따라 안전하게 종료한다.
     *
     * <ul>
     * <li>작동 중(run/pause): setMachineState(stop)으로 사이클 정상 종료. 원격 제어가 꺼져 있으면 종료하지
     * 않는다.</li>
     * <li>유휴(stop/off): switch off로 전원 정리.</li>
     * <li>상태 불명: 안전을 위해 종료하지 않는다.</li>
     * </ul>
     *
     * @return 종료 처리 결과
     */
    public ShutdownResult shutdown(Machine machine, SmartThingsDeviceStatusResDto status) {
        if (status == null) {
            log.warn("device status unknown, skip shutdown machine={} deviceId={}",
                    machine.getName(),
                    machine.getDeviceId());
            return ShutdownResult.SKIPPED_UNKNOWN;
        }

        if (isOperating(machine, status)) {
            if (!status.isRemoteControlEnabled()) {
                log.warn("device operating but remote control disabled, cannot stop safely machine={} deviceId={}",
                        machine.getName(),
                        machine.getDeviceId());
                return ShutdownResult.SKIPPED_REMOTE_DISABLED;
            }
            SmartThingsCommandReqDto stopCommand;
            if (machine.isWasher()) {
                stopCommand = SmartThingsCommandReqDto.stopWasher();
            } else if (machine.isDryer()) {
                stopCommand = SmartThingsCommandReqDto.stopDryer();
            } else {
                log.warn("unknown machine type, skip shutdown machine={} deviceId={}",
                        machine.getName(),
                        machine.getDeviceId());
                return ShutdownResult.SKIPPED_UNKNOWN;
            }
            sendDeviceCommandService.execute(machine.getDeviceId(), stopCommand);
            log.info("device safely stopped machine={} deviceId={}", machine.getName(), machine.getDeviceId());
            return ShutdownResult.STOPPED;
        }

        if ("off".equalsIgnoreCase(status.getSwitchStatus())) {
            log.info("device already powered off, skip command machine={} deviceId={}",
                    machine.getName(),
                    machine.getDeviceId());
            return ShutdownResult.POWERED_OFF;
        }

        sendDeviceCommandService.execute(machine.getDeviceId(), SmartThingsCommandReqDto.powerOff());
        log.info("idle device powered off machine={} deviceId={}", machine.getName(), machine.getDeviceId());
        return ShutdownResult.POWERED_OFF;
    }

    /**
     * 기기가 물리적으로 작동 중(run 또는 pause)인지 판정한다. 세탁기/건조기는 타입에 맞는 machineState를 본다.
     */
    private boolean isOperating(Machine machine, SmartThingsDeviceStatusResDto status) {
        String machineState = null;
        if (machine.isWasher()) {
            machineState = status.getWasherOperatingState();
        } else if (machine.isDryer()) {
            machineState = status.getDryerOperatingState();
        }
        return "run".equalsIgnoreCase(machineState) || "pause".equalsIgnoreCase(machineState);
    }
}
