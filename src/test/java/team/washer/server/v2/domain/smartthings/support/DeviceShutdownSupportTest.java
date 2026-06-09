package team.washer.server.v2.domain.smartthings.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import team.washer.server.v2.domain.machine.entity.Machine;
import team.washer.server.v2.domain.machine.enums.MachineAvailability;
import team.washer.server.v2.domain.machine.enums.MachineStatus;
import team.washer.server.v2.domain.machine.enums.MachineType;
import team.washer.server.v2.domain.machine.enums.Position;
import team.washer.server.v2.domain.smartthings.dto.request.SmartThingsCommandReqDto;
import team.washer.server.v2.domain.smartthings.dto.response.SmartThingsDeviceStatusResDto;
import team.washer.server.v2.domain.smartthings.dto.response.SmartThingsDeviceStatusResDto.AttributeState;
import team.washer.server.v2.domain.smartthings.dto.response.SmartThingsDeviceStatusResDto.ComponentStatus;
import team.washer.server.v2.domain.smartthings.dto.response.SmartThingsDeviceStatusResDto.DryerOperatingState;
import team.washer.server.v2.domain.smartthings.dto.response.SmartThingsDeviceStatusResDto.RemoteControlStatus;
import team.washer.server.v2.domain.smartthings.dto.response.SmartThingsDeviceStatusResDto.WasherOperatingState;
import team.washer.server.v2.domain.smartthings.service.SendDeviceCommandService;
import team.washer.server.v2.domain.smartthings.support.DeviceShutdownSupport.ShutdownResult;

@ExtendWith(MockitoExtension.class)
@DisplayName("DeviceShutdownSupport 안전 종료")
class DeviceShutdownSupportTest {

    @InjectMocks
    private DeviceShutdownSupport deviceShutdownSupport;

    @Mock
    private SendDeviceCommandService sendDeviceCommandService;

    private static AttributeState attr(String value) {
        return new AttributeState(value, null, null);
    }

    private Machine machine(MachineType type) {
        return Machine.builder().name("M-2F-L1").type(type).deviceId("device-1").floor(2).position(Position.LEFT)
                .number(1).status(MachineStatus.NORMAL).availability(MachineAvailability.AVAILABLE).build();
    }

    private SmartThingsDeviceStatusResDto washerStatus(String machineState, boolean remoteEnabled) {
        var washerOp = new WasherOperatingState(attr(machineState), null, null);
        var rc = new RemoteControlStatus(attr(String.valueOf(remoteEnabled)));
        return new SmartThingsDeviceStatusResDto(Map.of("main", new ComponentStatus(washerOp, null, null, rc)));
    }

    private SmartThingsDeviceStatusResDto dryerStatus(String machineState, boolean remoteEnabled) {
        var dryerOp = new DryerOperatingState(attr(machineState), null, null);
        var rc = new RemoteControlStatus(attr(String.valueOf(remoteEnabled)));
        return new SmartThingsDeviceStatusResDto(Map.of("main", new ComponentStatus(null, dryerOp, null, rc)));
    }

    @Nested
    @DisplayName("작동 중인 기기")
    class OperatingMachine {

        @Test
        @DisplayName("세탁기가 run이고 원격 제어가 켜져 있으면 setMachineState stop으로 안전 정지하고 전원을 차단하지 않는다")
        void shouldSafelyStopWasher_WhenRunningAndRemoteEnabled() {
            var machine = machine(MachineType.WASHER);

            var result = deviceShutdownSupport.shutdown(machine, washerStatus("run", true));

            assertThat(result).isEqualTo(ShutdownResult.STOPPED);
            var captor = ArgumentCaptor.forClass(SmartThingsCommandReqDto.class);
            then(sendDeviceCommandService).should(times(1)).execute(eq("device-1"), captor.capture());
            var command = captor.getValue().commands().get(0);
            assertThat(command.capability()).isEqualTo("washerOperatingState");
            assertThat(command.command()).isEqualTo("setMachineState");
            assertThat(command.arguments()).containsExactly("stop");
        }

        @Test
        @DisplayName("건조기가 pause이고 원격 제어가 켜져 있으면 dryerOperatingState로 안전 정지한다")
        void shouldSafelyStopDryer_WhenPausedAndRemoteEnabled() {
            var machine = machine(MachineType.DRYER);

            var result = deviceShutdownSupport.shutdown(machine, dryerStatus("pause", true));

            assertThat(result).isEqualTo(ShutdownResult.STOPPED);
            var captor = ArgumentCaptor.forClass(SmartThingsCommandReqDto.class);
            then(sendDeviceCommandService).should(times(1)).execute(eq("device-1"), captor.capture());
            assertThat(captor.getValue().commands().get(0).capability()).isEqualTo("dryerOperatingState");
        }

        @Test
        @DisplayName("작동 중이고 원격 제어가 꺼져 있어도 stop은 원격 제어 없이 동작하므로 정지를 시도하고 STOPPED를 반환한다")
        void shouldStop_WhenRunningEvenIfRemoteDisabled() {
            var machine = machine(MachineType.WASHER);

            var result = deviceShutdownSupport.shutdown(machine, washerStatus("run", false));

            assertThat(result).isEqualTo(ShutdownResult.STOPPED);
            var captor = ArgumentCaptor.forClass(SmartThingsCommandReqDto.class);
            then(sendDeviceCommandService).should(times(1)).execute(eq("device-1"), captor.capture());
            var command = captor.getValue().commands().get(0);
            assertThat(command.capability()).isEqualTo("washerOperatingState");
            assertThat(command.command()).isEqualTo("setMachineState");
            assertThat(command.arguments()).containsExactly("stop");
        }
    }

    @Nested
    @DisplayName("유휴/불명 기기")
    class IdleMachine {

        @Test
        @DisplayName("정지(stop) 상태인 유휴 기기는 switch off로 전원을 정리한다")
        void shouldPowerOff_WhenIdle() {
            var machine = machine(MachineType.WASHER);

            var result = deviceShutdownSupport.shutdown(machine, washerStatus("stop", false));

            assertThat(result).isEqualTo(ShutdownResult.POWERED_OFF);
            var captor = ArgumentCaptor.forClass(SmartThingsCommandReqDto.class);
            then(sendDeviceCommandService).should(times(1)).execute(eq("device-1"), captor.capture());
            var command = captor.getValue().commands().get(0);
            assertThat(command.capability()).isEqualTo("switch");
            assertThat(command.command()).isEqualTo("off");
        }

        @Test
        @DisplayName("상태가 null이면 안전을 위해 종료하지 않고 SKIPPED_UNKNOWN을 반환한다")
        void shouldSkip_WhenStatusNull() {
            var machine = machine(MachineType.WASHER);

            var result = deviceShutdownSupport.shutdown(machine, null);

            assertThat(result).isEqualTo(ShutdownResult.SKIPPED_UNKNOWN);
            then(sendDeviceCommandService).should(never()).execute(any(), any());
        }
    }
}
