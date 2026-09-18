package team.washer.server.v2.domain.smartthings.support;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import team.washer.server.v2.domain.machine.entity.Machine;
import team.washer.server.v2.domain.smartthings.dto.request.SmartThingsCommandReqDto;
import team.washer.server.v2.domain.smartthings.dto.response.SmartThingsDeviceStatusResDto;
import team.washer.server.v2.domain.smartthings.enums.MachineOperatingState;
import team.washer.server.v2.domain.smartthings.service.SendDeviceCommandService;

/**
 * 기기 전원을 차단하는 공통 컴포넌트.
 *
 * <p>
 * 서버는 기기 전원을 켜지 않으므로, 예약이 없는 기기의 전원을 꺼 두는 것이 사실상 잠금장치 역할을 한다. 그래서 예약 완료 시와 예약
 * 없이 켜진 기기 모두 작동 여부와 관계없이 전원을 차단한다({@code switch off}). 사이클만 끝내는
 * {@code setMachineState(stop)}은 전원이 켜진 채 남아 누구나 다시 시작할 수 있으므로 사용하지 않는다.
 *
 * <p>
 * 예외는 예약 완료 직후 아직 작동 중인 세탁기다. 배수·탈수 중 전원 차단을 피하기 위해 완료 시점에는 차단하지 않고, 유휴 기기 종료
 * 스케줄러가 배수 유예 이후 또는 정지를 확인한 뒤 차단한다.
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
        /** 전원을 차단했거나 이미 꺼져 있음 */
        POWERED_OFF,
        /** 배수 중일 수 있는 작동 중 세탁기라 전원을 차단하지 않음 */
        SKIPPED_WASHER_DRAINING,
        /** 기기 상태를 알 수 없어 종료하지 않음 */
        SKIPPED_UNKNOWN
    }

    /**
     * 기기 작동 여부와 관계없이 전원을 차단한다. 상태를 알 수 없으면 차단하지 않고, 이미 꺼져 있으면 명령을 보내지 않는다.
     *
     * @return 종료 처리 결과
     */
    public ShutdownResult shutdown(Machine machine, SmartThingsDeviceStatusResDto status) {
        return powerOff(machine.getName(), machine.getDeviceId(), machine.isWasher(), status);
    }

    /**
     * 예약 완료 후 기기 전원을 차단한다. 건조기는 작동 중이어도 즉시 차단해 구김방지 단계 진입을 막고, 세탁기는 작동 중이면 배수 중일 수
     * 있으므로 차단하지 않는다.
     *
     * @return 종료 처리 결과
     */
    public ShutdownResult shutdownAfterCompletion(String machineName,
            String deviceId,
            boolean isWasher,
            SmartThingsDeviceStatusResDto status) {
        if (isWasher && isOperating(status, true)) {
            log.info("washer still operating after completion, defer power off machine={} deviceId={} machineState={}",
                    machineName,
                    deviceId,
                    status.getOperatingState(true));
            return ShutdownResult.SKIPPED_WASHER_DRAINING;
        }
        return powerOff(machineName, deviceId, isWasher, status);
    }

    /**
     * 기기가 물리적으로 작동 중(run 또는 pause)인지 판정한다. 세탁기/건조기는 타입에 맞는 machineState를 본다.
     */
    public boolean isOperating(SmartThingsDeviceStatusResDto status, boolean isWasher) {
        if (status == null) {
            return false;
        }
        var machineState = status.getOperatingState(isWasher);
        return machineState == MachineOperatingState.RUN || machineState == MachineOperatingState.PAUSE;
    }

    private ShutdownResult powerOff(String machineName,
            String deviceId,
            boolean isWasher,
            SmartThingsDeviceStatusResDto status) {
        if (status == null) {
            log.warn("device status unknown, skip shutdown machine={} deviceId={}", machineName, deviceId);
            return ShutdownResult.SKIPPED_UNKNOWN;
        }

        if (status.isSwitchOff()) {
            log.info("device already powered off, skip command machine={} deviceId={}", machineName, deviceId);
            return ShutdownResult.POWERED_OFF;
        }

        sendDeviceCommandService.execute(deviceId, SmartThingsCommandReqDto.powerOff());
        log.info("device powered off machine={} deviceId={} machineState={}",
                machineName,
                deviceId,
                status.getOperatingState(isWasher));
        return ShutdownResult.POWERED_OFF;
    }
}
