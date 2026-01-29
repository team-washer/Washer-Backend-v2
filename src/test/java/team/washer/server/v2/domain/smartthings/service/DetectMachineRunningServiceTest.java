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
import team.washer.server.v2.domain.smartthings.service.impl.DetectMachineRunningServiceImpl;

@ExtendWith(MockitoExtension.class)
class DetectMachineRunningServiceTest {

    @InjectMocks
    private DetectMachineRunningServiceImpl detectMachineRunningService;

    @Mock
    private QueryDeviceStatusService queryDeviceStatusService;

    @Nested
    @DisplayName("기기 작동 감지")
    class ExecuteTest {

        @Test
        @DisplayName("세탁기가 작동 중이면 true를 반환한다")
        void execute_ShouldReturnTrue_WhenWasherIsRunning() {
            // Given
            String deviceId = "device-123";

            var operatingStateValue = new SmartThingsDeviceStatusResDto.Value("running", "2026-01-26T14:30:00Z", null);
            var operatingStateCapability = new SmartThingsDeviceStatusResDto.CapabilityStatus(operatingStateValue);
            var componentStatus = new SmartThingsDeviceStatusResDto.ComponentStatus(operatingStateCapability,
                    null,
                    null,
                    null,
                    null,
                    null);
            var deviceStatus = new SmartThingsDeviceStatusResDto(Map.of("main", componentStatus));

            when(queryDeviceStatusService.execute(deviceId)).thenReturn(deviceStatus);

            // When
            var result = detectMachineRunningService.execute(deviceId);

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("건조기가 작동 중이면 true를 반환한다")
        void execute_ShouldReturnTrue_WhenDryerIsRunning() {
            // Given
            String deviceId = "device-456";

            var operatingStateValue = new SmartThingsDeviceStatusResDto.Value("running", "2026-01-26T14:30:00Z", null);
            var operatingStateCapability = new SmartThingsDeviceStatusResDto.CapabilityStatus(operatingStateValue);
            var componentStatus = new SmartThingsDeviceStatusResDto.ComponentStatus(null,
                    operatingStateCapability,
                    null,
                    null,
                    null,
                    null);
            var deviceStatus = new SmartThingsDeviceStatusResDto(Map.of("main", componentStatus));

            when(queryDeviceStatusService.execute(deviceId)).thenReturn(deviceStatus);

            // When
            var result = detectMachineRunningService.execute(deviceId);

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("기기가 작동 중이지 않으면 false를 반환한다")
        void execute_ShouldReturnFalse_WhenMachineIsNotRunning() {
            // Given
            String deviceId = "device-789";

            var operatingStateValue = new SmartThingsDeviceStatusResDto.Value("pause", "2026-01-26T14:30:00Z", null);
            var operatingStateCapability = new SmartThingsDeviceStatusResDto.CapabilityStatus(operatingStateValue);
            var componentStatus = new SmartThingsDeviceStatusResDto.ComponentStatus(operatingStateCapability,
                    null,
                    null,
                    null,
                    null,
                    null);
            var deviceStatus = new SmartThingsDeviceStatusResDto(Map.of("main", componentStatus));

            when(queryDeviceStatusService.execute(deviceId)).thenReturn(deviceStatus);

            // When
            var result = detectMachineRunningService.execute(deviceId);

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("API 호출 실패 시 false를 반환한다")
        void execute_ShouldReturnFalse_WhenApiCallFails() {
            // Given
            String deviceId = "device-error";

            when(queryDeviceStatusService.execute(deviceId)).thenThrow(new RuntimeException("API Error"));

            // When
            var result = detectMachineRunningService.execute(deviceId);

            // Then
            assertThat(result).isFalse();
        }
    }
}
