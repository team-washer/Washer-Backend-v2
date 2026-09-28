package team.washer.server.v2.domain.reservation.support;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import team.themoment.sdk.exception.ExpectedException;
import team.washer.server.v2.domain.machine.entity.Machine;
import team.washer.server.v2.domain.smartthings.dto.response.SmartThingsDeviceStatusResDto;
import team.washer.server.v2.domain.smartthings.enums.MachineOperatingState;
import team.washer.server.v2.domain.smartthings.support.DeviceStatusQuerySupport;
import team.washer.server.v2.global.common.error.code.ErrorCode;
import team.washer.server.v2.global.common.error.exception.ErrorCodeException;

/**
 * 예약 생성 전에 SmartThings가 보고하는 실제 기기 작동 상태를 확인하는 컴포넌트.
 *
 * <p>
 * DB에 활성 예약이 없어도 기기가 물리적으로 작동 중(run 또는 pause)이면 누군가 예약 없이 사용 중이므로 예약을 거부한다. 상태를
 * 확인할 수 없는 경우(조회 실패·타임아웃·응답 누락·알 수 없는 상태)에도 사용 중인 기기를 예약하는 일이 없도록 예약을 허용하지 않는다.
 *
 * <p>
 * 외부 HTTP 호출이 DB 락·커넥션을 점유하지 않도록 반드시 트랜잭션 밖에서 호출해야 한다. 재시도는 Feign 설정의 제한된 횟수만
 * 따른다. 확인 직후부터 기기 락을 획득하기까지의 짧은 구간에 기기가 작동을 시작하는 경우는 막을 수 없으며, 그 이후의 점유 불일치는 예약
 * 라이프사이클 스케줄러가 정리한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReservationDeviceStateVerifier {

    private final DeviceStatusQuerySupport deviceStatusQuerySupport;

    /**
     * 기기가 작동 중이 아니고 예약 가능한 정지 상태인지 확인합니다.
     *
     * @param machine
     *            예약 대상 기기
     * @throws ExpectedException
     *             기기가 작동 중이면 {@code 409 CONFLICT}
     * @throws ErrorCodeException
     *             상태를 확인할 수 없으면 {@link ErrorCode#MACHINE_STATE_UNAVAILABLE}
     */
    public void verifyNotOperating(final Machine machine) {
        final SmartThingsDeviceStatusResDto deviceStatus = queryDeviceStatus(machine);
        if (deviceStatus == null) {
            log.warn("reservation device state missing machineId={} deviceId={}",
                    machine.getId(),
                    machine.getDeviceId());
            throw new ErrorCodeException(ErrorCode.MACHINE_STATE_UNAVAILABLE);
        }

        // 전원이 꺼진 기기는 사이클을 진행할 수 없으므로 정지 상태로 본다
        if (deviceStatus.isSwitchOff()) {
            log.info("reservation device state verified machineId={} deviceId={} switch=off",
                    machine.getId(),
                    machine.getDeviceId());
            return;
        }

        final MachineOperatingState operatingState = deviceStatus.getOperatingState(machine.isWasher());
        final String stateTimestamp = deviceStatus.getOperatingStateTimestamp(machine.isWasher());
        if (operatingState.isOperating()) {
            log.info(
                    "reservation rejected machine operating machineId={} deviceId={} operatingState={} stateTimestamp={}",
                    machine.getId(),
                    machine.getDeviceId(),
                    operatingState,
                    stateTimestamp);
            throw new ExpectedException(String.format("해당 기기가 현재 작동 중이어서 예약할 수 없습니다. 기기: %s", machine.getName()),
                    HttpStatus.CONFLICT);
        }
        if (operatingState != MachineOperatingState.STOP) {
            log.warn("reservation device state unknown machineId={} deviceId={} operatingState={}",
                    machine.getId(),
                    machine.getDeviceId(),
                    operatingState);
            throw new ErrorCodeException(ErrorCode.MACHINE_STATE_UNAVAILABLE);
        }

        // 확인 시점 이후 기기 락 획득 전까지의 상태 변화는 감지하지 못하므로 추적을 위해 상태 갱신 시각을 남긴다
        log.info("reservation device state verified machineId={} deviceId={} operatingState={} stateTimestamp={}",
                machine.getId(),
                machine.getDeviceId(),
                operatingState,
                stateTimestamp);
    }

    private SmartThingsDeviceStatusResDto queryDeviceStatus(final Machine machine) {
        try {
            return deviceStatusQuerySupport.queryDeviceStatus(machine.getDeviceId());
        } catch (final Exception e) {
            log.warn("reservation device state query failed machineId={} deviceId={}",
                    machine.getId(),
                    machine.getDeviceId(),
                    e);
            throw new ErrorCodeException(ErrorCode.MACHINE_STATE_UNAVAILABLE, e);
        }
    }
}
