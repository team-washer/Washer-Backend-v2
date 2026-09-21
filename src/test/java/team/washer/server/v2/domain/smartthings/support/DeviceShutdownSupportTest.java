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
import team.washer.server.v2.domain.smartthings.dto.response.SmartThingsDeviceStatusResDto.SwitchCapability;
import team.washer.server.v2.domain.smartthings.dto.response.SmartThingsDeviceStatusResDto.WasherOperatingState;
import team.washer.server.v2.domain.smartthings.service.SendDeviceCommandService;
import team.washer.server.v2.domain.smartthings.support.DeviceShutdownSupport.ShutdownResult;

@ExtendWith(MockitoExtension.class)
@DisplayName("DeviceShutdownSupport 전원 차단")
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

    private SmartThingsDeviceStatusResDto washerStatus(String machineState, String switchState) {
        var washerOp = new WasherOperatingState(attr(machineState), null, null);
        var switchCapability = new SwitchCapability(attr(switchState));
        return new SmartThingsDeviceStatusResDto(
                Map.of("main", new ComponentStatus(washerOp, null, switchCapability, null)));
    }

    private SmartThingsDeviceStatusResDto dryerStatus(String machineState, String switchState) {
        var dryerOp = new DryerOperatingState(attr(machineState), null, null);
        var switchCapability = new SwitchCapability(attr(switchState));
        return new SmartThingsDeviceStatusResDto(
                Map.of("main", new ComponentStatus(null, dryerOp, switchCapability, null)));
    }

    private void assertPowerOffSent() {
        var captor = ArgumentCaptor.forClass(SmartThingsCommandReqDto.class);
        then(sendDeviceCommandService).should(times(1)).execute(eq("device-1"), captor.capture());
        var command = captor.getValue().commands().get(0);
        assertThat(command.capability()).isEqualTo("switch");
        assertThat(command.command()).isEqualTo("off");
    }

    @Nested
    @DisplayName("shutdown 메서드는")
    class Shutdown {

        @Test
        @DisplayName("작동 중인 세탁기도 전원을 차단한다")
        void shouldPowerOff_WhenWasherRunning() {
            // Given
            var machine = machine(MachineType.WASHER);

            // When
            var result = deviceShutdownSupport.shutdown(machine, washerStatus("run", "on"));

            // Then
            assertThat(result).isEqualTo(ShutdownResult.POWERED_OFF);
            assertPowerOffSent();
        }

        @Test
        @DisplayName("일시정지 중인 건조기도 전원을 차단한다")
        void shouldPowerOff_WhenDryerPaused() {
            // Given
            var machine = machine(MachineType.DRYER);

            // When
            var result = deviceShutdownSupport.shutdown(machine, dryerStatus("pause", "on"));

            // Then
            assertThat(result).isEqualTo(ShutdownResult.POWERED_OFF);
            assertPowerOffSent();
        }

        @Test
        @DisplayName("정지 상태인 유휴 기기의 전원을 차단한다")
        void shouldPowerOff_WhenIdle() {
            // Given
            var machine = machine(MachineType.WASHER);

            // When
            var result = deviceShutdownSupport.shutdown(machine, washerStatus("stop", "on"));

            // Then
            assertThat(result).isEqualTo(ShutdownResult.POWERED_OFF);
            assertPowerOffSent();
        }

        @Test
        @DisplayName("이미 전원이 꺼져 있으면 명령을 보내지 않는다")
        void shouldSkipCommand_WhenAlreadyOff() {
            // Given
            var machine = machine(MachineType.WASHER);

            // When
            var result = deviceShutdownSupport.shutdown(machine, washerStatus("stop", "off"));

            // Then
            assertThat(result).isEqualTo(ShutdownResult.POWERED_OFF);
            then(sendDeviceCommandService).should(never()).execute(any(), any());
        }

        @Test
        @DisplayName("machineState를 읽을 수 없으면 종료하지 않고 SKIPPED_UNKNOWN을 반환한다")
        void shouldSkip_WhenMachineStateUnknown() {
            // Given
            var machine = machine(MachineType.WASHER);

            // When
            var result = deviceShutdownSupport.shutdown(machine, washerStatus(null, "on"));

            // Then
            assertThat(result).isEqualTo(ShutdownResult.SKIPPED_UNKNOWN);
            then(sendDeviceCommandService).should(never()).execute(any(), any());
        }

        @Test
        @DisplayName("상태가 null이면 안전을 위해 종료하지 않고 SKIPPED_UNKNOWN을 반환한다")
        void shouldSkip_WhenStatusNull() {
            // Given
            var machine = machine(MachineType.WASHER);

            // When
            var result = deviceShutdownSupport.shutdown(machine, null);

            // Then
            assertThat(result).isEqualTo(ShutdownResult.SKIPPED_UNKNOWN);
            then(sendDeviceCommandService).should(never()).execute(any(), any());
        }
    }

    @Nested
    @DisplayName("shutdownAfterCompletion 메서드는")
    class ShutdownAfterCompletion {

        @Test
        @DisplayName("건조기는 작동 중(구김방지)이어도 즉시 전원을 차단한다")
        void shouldPowerOffDryer_WhenRunning() {
            // When
            var result = deviceShutdownSupport
                    .shutdownAfterCompletion("M-2F-L1", "device-1", false, dryerStatus("run", "on"));

            // Then
            assertThat(result).isEqualTo(ShutdownResult.POWERED_OFF);
            assertPowerOffSent();
        }

        @Test
        @DisplayName("세탁기가 정지 상태면 즉시 전원을 차단한다")
        void shouldPowerOffWasher_WhenStopped() {
            // When
            var result = deviceShutdownSupport
                    .shutdownAfterCompletion("M-2F-L1", "device-1", true, washerStatus("stop", "on"));

            // Then
            assertThat(result).isEqualTo(ShutdownResult.POWERED_OFF);
            assertPowerOffSent();
        }

        @Test
        @DisplayName("세탁기가 작동 중이면 배수 중일 수 있으므로 전원을 차단하지 않는다")
        void shouldSkipWasher_WhenRunning() {
            // When
            var result = deviceShutdownSupport
                    .shutdownAfterCompletion("M-2F-L1", "device-1", true, washerStatus("run", "on"));

            // Then
            assertThat(result).isEqualTo(ShutdownResult.SKIPPED_WASHER_DRAINING);
            then(sendDeviceCommandService).should(never()).execute(any(), any());
        }
    }
}
