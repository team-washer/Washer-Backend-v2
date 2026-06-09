package team.washer.server.v2.domain.smartthings.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import team.washer.server.v2.domain.smartthings.dto.response.SmartThingsDeviceStatusResDto;
import team.washer.server.v2.domain.smartthings.dto.response.SmartThingsDeviceStatusResDto.AttributeState;
import team.washer.server.v2.domain.smartthings.dto.response.SmartThingsDeviceStatusResDto.ComponentStatus;
import team.washer.server.v2.domain.smartthings.dto.response.SmartThingsDeviceStatusResDto.DryerOperatingState;
import team.washer.server.v2.domain.smartthings.dto.response.SmartThingsDeviceStatusResDto.WasherOperatingState;

@ExtendWith(MockitoExtension.class)
@DisplayName("MachineStateDetectionSupport 완료 판정")
class MachineStateDetectionSupportTest {

    @InjectMocks
    private MachineStateDetectionSupport machineStateDetectionSupport;

    @Mock
    private DeviceStatusQuerySupport deviceStatusQuerySupport;

    private static String isoUtc(ZonedDateTime koreaTime) {
        return koreaTime.withZoneSameInstant(ZoneId.of("UTC")).toLocalDateTime().toString() + "Z";
    }

    private static AttributeState attr(String value) {
        return value == null ? null : new AttributeState(value, null, null);
    }

    private static SmartThingsDeviceStatusResDto washerStatus(String machineState,
            String jobState,
            String completionTime) {
        var washerOpState = new WasherOperatingState(attr(machineState), attr(jobState), attr(completionTime));
        return new SmartThingsDeviceStatusResDto(Map.of("main", new ComponentStatus(washerOpState, null, null, null)));
    }

    private static SmartThingsDeviceStatusResDto dryerStatus(String machineState,
            String jobState,
            String completionTime) {
        var dryerOpState = new DryerOperatingState(attr(machineState), attr(jobState), attr(completionTime));
        return new SmartThingsDeviceStatusResDto(Map.of("main", new ComponentStatus(null, dryerOpState, null, null)));
    }

    @Nested
    @DisplayName("세탁기 완료 판정")
    class WasherCompletion {

        @Test
        @DisplayName("jobState=finish, machineState=stop, 완료 시각이 지났으면 완료로 판정한다")
        void shouldComplete_WhenFinishedStoppedAndTimePassed() {
            var past = ZonedDateTime.now(ZoneId.of("Asia/Seoul")).minusMinutes(1);
            var status = washerStatus("stop", "finish", isoUtc(past));

            var result = machineStateDetectionSupport.isCompleted(status);

            assertThat(result).isPresent();
        }

        @Test
        @DisplayName("jobState=finish 이지만 machineState=run 이면 조기 보고로 보고 미완료로 판정한다")
        void shouldNotComplete_WhenFinishedButStillRunning() {
            var past = ZonedDateTime.now(ZoneId.of("Asia/Seoul")).minusMinutes(1);
            var status = washerStatus("run", "finish", isoUtc(past));

            var result = machineStateDetectionSupport.isCompleted(status);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("jobState=finish, machineState=stop 이지만 완료 시각이 미래(잔여시간 남음)면 미완료로 판정한다")
        void shouldNotComplete_WhenFinishedButCompletionTimeInFuture() {
            var future = ZonedDateTime.now(ZoneId.of("Asia/Seoul")).plusMinutes(3);
            var status = washerStatus("stop", "finish", isoUtc(future));

            var result = machineStateDetectionSupport.isCompleted(status);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("jobState가 finish 가 아니면 미완료로 판정한다")
        void shouldNotComplete_WhenJobStateNotFinished() {
            var status = washerStatus("run", "spin", null);

            var result = machineStateDetectionSupport.isCompleted(status);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("completionTime 이 없어도 jobState=finish, machineState=stop 이면 완료로 판정한다")
        void shouldComplete_WhenCompletionTimeNull() {
            var status = washerStatus("stop", "finish", null);

            var result = machineStateDetectionSupport.isCompleted(status);

            assertThat(result).isPresent();
        }
    }

    @Nested
    @DisplayName("건조기 완료 판정")
    class DryerCompletion {

        @Test
        @DisplayName("jobState=finished, machineState=stop, 완료 시각이 지났으면 완료로 판정한다")
        void shouldComplete_WhenFinishedStoppedAndTimePassed() {
            var past = ZonedDateTime.now(ZoneId.of("Asia/Seoul")).minusMinutes(1);
            var status = dryerStatus("stop", "finished", isoUtc(past));

            var result = machineStateDetectionSupport.isCompleted(status);

            assertThat(result).isPresent();
        }

        @Test
        @DisplayName("jobState=cooling 잔여 단계이고 machineState=run 이면 미완료로 판정한다")
        void shouldNotComplete_WhenCoolingAndRunning() {
            var future = ZonedDateTime.now(ZoneId.of("Asia/Seoul")).plusMinutes(2);
            var status = dryerStatus("run", "cooling", isoUtc(future));

            var result = machineStateDetectionSupport.isCompleted(status);

            assertThat(result).isEmpty();
        }
    }
}
