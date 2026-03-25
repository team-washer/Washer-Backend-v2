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
import team.washer.server.v2.domain.smartthings.service.impl.DetectMachinePausedServiceImpl;

@ExtendWith(MockitoExtension.class)
class DetectMachinePausedServiceTest {

    @InjectMocks
    private DetectMachinePausedServiceImpl detectMachinePausedService;

    @Mock
    private QueryDeviceStatusService queryDeviceStatusService;

    private SmartThingsDeviceStatusResDto buildStatus(String washerMachineState, String dryerMachineState) {
        var washerMachineAttr = washerMachineState != null
                ? new SmartThingsDeviceStatusResDto.AttributeState(washerMachineState, null, null)
                : null;
        var washerOpState = washerMachineAttr != null
                ? new SmartThingsDeviceStatusResDto.WasherOperatingState(washerMachineAttr, null, null)
                : null;

        var dryerMachineAttr = dryerMachineState != null
                ? new SmartThingsDeviceStatusResDto.AttributeState(dryerMachineState, null, null)
                : null;
        var dryerOpState = dryerMachineAttr != null
                ? new SmartThingsDeviceStatusResDto.DryerOperatingState(dryerMachineAttr, null, null)
                : null;

        var componentStatus = new SmartThingsDeviceStatusResDto.ComponentStatus(washerOpState, dryerOpState, null);
        return new SmartThingsDeviceStatusResDto(Map.of("main", componentStatus));
    }

    @Nested
    @DisplayName("세탁기가 일시정지 상태인 경우")
    class WasherPaused {

        @Test
        @DisplayName("세탁기 machineState가 pause이면 true를 반환한다")
        void execute_ShouldReturnTrue_WhenWasherIsPaused() {
            // Given
            String deviceId = "device-123";
            when(queryDeviceStatusService.execute(deviceId)).thenReturn(buildStatus("pause", null));

            // When & Then
            assertThat(detectMachinePausedService.execute(deviceId)).isTrue();
        }

        @Test
        @DisplayName("세탁기 machineState가 run이면 false를 반환한다")
        void execute_ShouldReturnFalse_WhenWasherIsRunning() {
            // Given
            String deviceId = "device-123";
            when(queryDeviceStatusService.execute(deviceId)).thenReturn(buildStatus("run", null));

            // When & Then
            assertThat(detectMachinePausedService.execute(deviceId)).isFalse();
        }
    }

    @Nested
    @DisplayName("건조기가 일시정지 상태인 경우")
    class DryerPaused {

        @Test
        @DisplayName("건조기 machineState가 pause이면 true를 반환한다")
        void execute_ShouldReturnTrue_WhenDryerIsPaused() {
            // Given
            String deviceId = "device-456";
            when(queryDeviceStatusService.execute(deviceId)).thenReturn(buildStatus(null, "pause"));

            // When & Then
            assertThat(detectMachinePausedService.execute(deviceId)).isTrue();
        }

        @Test
        @DisplayName("건조기 machineState가 drying이면 false를 반환한다")
        void execute_ShouldReturnFalse_WhenDryerIsRunning() {
            // Given
            String deviceId = "device-456";
            when(queryDeviceStatusService.execute(deviceId)).thenReturn(buildStatus(null, "drying"));

            // When & Then
            assertThat(detectMachinePausedService.execute(deviceId)).isFalse();
        }
    }

    @Nested
    @DisplayName("기기가 정상 작동 중인 경우")
    class MachineRunning {

        @Test
        @DisplayName("세탁기와 건조기 모두 run 상태이면 false를 반환한다")
        void execute_ShouldReturnFalse_WhenBothRunning() {
            // Given
            String deviceId = "device-123";
            when(queryDeviceStatusService.execute(deviceId)).thenReturn(buildStatus("run", "drying"));

            // When & Then
            assertThat(detectMachinePausedService.execute(deviceId)).isFalse();
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
            assertThat(detectMachinePausedService.execute(deviceId)).isFalse();
        }
    }
}
