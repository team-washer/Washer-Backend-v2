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
import team.washer.server.v2.domain.smartthings.service.impl.DetectMachineCompletionServiceImpl;

@ExtendWith(MockitoExtension.class)
class DetectMachineCompletionServiceTest {

    @InjectMocks
    private DetectMachineCompletionServiceImpl detectMachineCompletionService;

    @Mock
    private QueryDeviceStatusService queryDeviceStatusService;

    @Nested
    @DisplayName("기기 완료 감지")
    class ExecuteTest {

        @Test
        @DisplayName("세탁기 작업이 완료되면 완료 시간을 반환한다")
        void execute_ShouldReturnCompletionTime_WhenWasherJobFinished() {
            // Given
            String deviceId = "device-123";

            var jobStateValue = new SmartThingsDeviceStatusResDto.Value("finished", "2026-01-26T14:30:00Z", null);
            var jobStateCapability = new SmartThingsDeviceStatusResDto.CapabilityStatus(jobStateValue);

            var completionTimeValue = new SmartThingsDeviceStatusResDto.Value("2026-01-26T15:30:00Z",
                    "2026-01-26T14:30:00Z",
                    null);
            var completionTimeCapability = new SmartThingsDeviceStatusResDto.CapabilityStatus(completionTimeValue);

            var componentStatus = new SmartThingsDeviceStatusResDto.ComponentStatus(null,
                    null,
                    jobStateCapability,
                    null,
                    completionTimeCapability,
                    null);
            var deviceStatus = new SmartThingsDeviceStatusResDto(Map.of("main", componentStatus));

            when(queryDeviceStatusService.execute(deviceId)).thenReturn(deviceStatus);

            // When
            var result = detectMachineCompletionService.execute(deviceId);

            // Then
            assertThat(result).isPresent();
        }

        @Test
        @DisplayName("건조기 작업이 완료되면 완료 시간을 반환한다")
        void execute_ShouldReturnCompletionTime_WhenDryerJobFinished() {
            // Given
            String deviceId = "device-456";

            var jobStateValue = new SmartThingsDeviceStatusResDto.Value("finished", "2026-01-26T14:30:00Z", null);
            var jobStateCapability = new SmartThingsDeviceStatusResDto.CapabilityStatus(jobStateValue);

            var completionTimeValue = new SmartThingsDeviceStatusResDto.Value("2026-01-26T15:30:00Z",
                    "2026-01-26T14:30:00Z",
                    null);
            var completionTimeCapability = new SmartThingsDeviceStatusResDto.CapabilityStatus(completionTimeValue);

            var componentStatus = new SmartThingsDeviceStatusResDto.ComponentStatus(null,
                    null,
                    null,
                    jobStateCapability,
                    completionTimeCapability,
                    null);
            var deviceStatus = new SmartThingsDeviceStatusResDto(Map.of("main", componentStatus));

            when(queryDeviceStatusService.execute(deviceId)).thenReturn(deviceStatus);

            // When
            var result = detectMachineCompletionService.execute(deviceId);

            // Then
            assertThat(result).isPresent();
        }

        @Test
        @DisplayName("작업이 완료되지 않았으면 빈 Optional을 반환한다")
        void execute_ShouldReturnEmpty_WhenJobNotFinished() {
            // Given
            String deviceId = "device-789";

            var jobStateValue = new SmartThingsDeviceStatusResDto.Value("run", "2026-01-26T14:30:00Z", null);
            var jobStateCapability = new SmartThingsDeviceStatusResDto.CapabilityStatus(jobStateValue);

            var componentStatus = new SmartThingsDeviceStatusResDto.ComponentStatus(null,
                    null,
                    jobStateCapability,
                    null,
                    null,
                    null);
            var deviceStatus = new SmartThingsDeviceStatusResDto(Map.of("main", componentStatus));

            when(queryDeviceStatusService.execute(deviceId)).thenReturn(deviceStatus);

            // When
            var result = detectMachineCompletionService.execute(deviceId);

            // Then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("API 호출 실패 시 빈 Optional을 반환한다")
        void execute_ShouldReturnEmpty_WhenApiCallFails() {
            // Given
            String deviceId = "device-error";

            when(queryDeviceStatusService.execute(deviceId)).thenThrow(new RuntimeException("API Error"));

            // When
            var result = detectMachineCompletionService.execute(deviceId);

            // Then
            assertThat(result).isEmpty();
        }
    }
}
