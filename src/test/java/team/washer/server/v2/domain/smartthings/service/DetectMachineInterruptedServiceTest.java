package team.washer.server.v2.domain.smartthings.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import team.washer.server.v2.domain.smartthings.dto.response.SmartThingsDeviceStatusResDto;
import team.washer.server.v2.domain.smartthings.service.impl.DetectMachineInterruptedServiceImpl;

@ExtendWith(MockitoExtension.class)
class DetectMachineInterruptedServiceTest {

    @InjectMocks
    private DetectMachineInterruptedServiceImpl detectMachineInterruptedService;

    @Mock
    private QueryDeviceStatusService queryDeviceStatusService;

    private SmartThingsDeviceStatusResDto buildStatus(String switchState,
            String washerMachineState,
            String washerJobState,
            String dryerMachineState,
            String dryerJobState) {
        var switchAttr = switchState != null
                ? new SmartThingsDeviceStatusResDto.AttributeState(switchState, null, null)
                : null;
        var switchCap = switchAttr != null ? new SmartThingsDeviceStatusResDto.SwitchCapability(switchAttr) : null;

        var washerMachineAttr = washerMachineState != null
                ? new SmartThingsDeviceStatusResDto.AttributeState(washerMachineState, null, null)
                : null;
        var washerJobAttr = washerJobState != null
                ? new SmartThingsDeviceStatusResDto.AttributeState(washerJobState, null, null)
                : null;
        var washerOpState = (washerMachineAttr != null || washerJobAttr != null)
                ? new SmartThingsDeviceStatusResDto.WasherOperatingState(washerMachineAttr, washerJobAttr, null)
                : null;

        var dryerMachineAttr = dryerMachineState != null
                ? new SmartThingsDeviceStatusResDto.AttributeState(dryerMachineState, null, null)
                : null;
        var dryerJobAttr = dryerJobState != null
                ? new SmartThingsDeviceStatusResDto.AttributeState(dryerJobState, null, null)
                : null;
        var dryerOpState = (dryerMachineAttr != null || dryerJobAttr != null)
                ? new SmartThingsDeviceStatusResDto.DryerOperatingState(dryerMachineAttr, dryerJobAttr, null)
                : null;

        var componentStatus = new SmartThingsDeviceStatusResDto.ComponentStatus(washerOpState, dryerOpState, switchCap);
        return new SmartThingsDeviceStatusResDto(Map.of("main", componentStatus));
    }

    @Nested
    @DisplayName("전원이 꺼진 경우")
    class SwitchOff {

        @Test
        @DisplayName("switch가 off이면 true를 반환한다")
        void execute_ShouldReturnTrue_WhenSwitchIsOff() {
            // Given
            String deviceId = "device-123";
            when(queryDeviceStatusService.execute(deviceId)).thenReturn(buildStatus("off", null, null, null, null));

            // When & Then
            assertThat(detectMachineInterruptedService.execute(deviceId)).isTrue();
        }

        @Test
        @DisplayName("switch가 on이면 전원 꺼짐으로 판단하지 않는다")
        void execute_ShouldNotTrigger_WhenSwitchIsOn() {
            // Given
            String deviceId = "device-123";
            when(queryDeviceStatusService.execute(deviceId)).thenReturn(buildStatus("on", "run", "wash", null, null));

            // When & Then
            assertThat(detectMachineInterruptedService.execute(deviceId)).isFalse();
        }
    }

    @Nested
    @DisplayName("세탁기가 비정상 정지된 경우")
    class WasherAbnormalStop {

        @Test
        @DisplayName("세탁기 machineState가 stop이고 jobState가 finish가 아니면 true를 반환한다")
        void execute_ShouldReturnTrue_WhenWasherStoppedWithoutFinish() {
            // Given
            String deviceId = "device-123";
            when(queryDeviceStatusService.execute(deviceId)).thenReturn(buildStatus("on", "stop", "wash", null, null));

            // When & Then
            assertThat(detectMachineInterruptedService.execute(deviceId)).isTrue();
        }

        @Test
        @DisplayName("세탁기 machineState가 stop이고 jobState가 finish이면 정상 완료로 판단한다")
        void execute_ShouldReturnFalse_WhenWasherStoppedWithFinish() {
            // Given
            String deviceId = "device-123";
            when(queryDeviceStatusService.execute(deviceId))
                    .thenReturn(buildStatus("on", "stop", "finish", null, null));

            // When & Then
            assertThat(detectMachineInterruptedService.execute(deviceId)).isFalse();
        }
    }

    @Nested
    @DisplayName("건조기가 비정상 정지된 경우")
    class DryerAbnormalStop {

        @Test
        @DisplayName("건조기 machineState가 stop이고 jobState가 finished가 아니면 true를 반환한다")
        void execute_ShouldReturnTrue_WhenDryerStoppedWithoutFinish() {
            // Given
            String deviceId = "device-456";
            when(queryDeviceStatusService.execute(deviceId))
                    .thenReturn(buildStatus("on", null, null, "stop", "drying"));

            // When & Then
            assertThat(detectMachineInterruptedService.execute(deviceId)).isTrue();
        }

        @Test
        @DisplayName("건조기 machineState가 stop이고 jobState가 finished이면 정상 완료로 판단한다")
        void execute_ShouldReturnFalse_WhenDryerStoppedWithFinish() {
            // Given
            String deviceId = "device-456";
            when(queryDeviceStatusService.execute(deviceId))
                    .thenReturn(buildStatus("on", null, null, "stop", "finished"));

            // When & Then
            assertThat(detectMachineInterruptedService.execute(deviceId)).isFalse();
        }
    }

    @Nested
    @DisplayName("기기가 정상 작동 중인 경우")
    class MachineRunning {

        @Test
        @DisplayName("machineState가 run이면 false를 반환한다")
        void execute_ShouldReturnFalse_WhenMachineIsRunning() {
            // Given
            String deviceId = "device-123";
            when(queryDeviceStatusService.execute(deviceId)).thenReturn(buildStatus("on", "run", "wash", null, null));

            // When & Then
            assertThat(detectMachineInterruptedService.execute(deviceId)).isFalse();
        }
    }

    @Nested
    @DisplayName("API 호출 실패")
    class ApiFailure {

        @Test
        @DisplayName("API 호출 실패 시 false를 반환한다")
        void execute_ShouldReturnFalse_WhenApiCallFails() {
            // Given
            String deviceId = "device-error";
            when(queryDeviceStatusService.execute(deviceId)).thenThrow(new RuntimeException("API Error"));

            // When & Then
            assertThat(detectMachineInterruptedService.execute(deviceId)).isFalse();
        }
    }
}
