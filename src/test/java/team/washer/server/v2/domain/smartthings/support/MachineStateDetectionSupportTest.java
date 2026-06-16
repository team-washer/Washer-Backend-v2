package team.washer.server.v2.domain.smartthings.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import team.washer.server.v2.domain.smartthings.dto.response.SmartThingsDeviceStatusResDto;
import team.washer.server.v2.domain.smartthings.dto.response.SmartThingsDeviceStatusResDto.AttributeState;
import team.washer.server.v2.domain.smartthings.dto.response.SmartThingsDeviceStatusResDto.ComponentStatus;
import team.washer.server.v2.domain.smartthings.dto.response.SmartThingsDeviceStatusResDto.DryerOperatingState;
import team.washer.server.v2.domain.smartthings.dto.response.SmartThingsDeviceStatusResDto.SwitchCapability;
import team.washer.server.v2.domain.smartthings.dto.response.SmartThingsDeviceStatusResDto.WasherOperatingState;

@DisplayName("MachineStateDetectionSupport 상태 판정")
class MachineStateDetectionSupportTest {

    private static final boolean WASHER = true;
    private static final boolean DRYER = false;

    private final MachineStateDetectionSupport machineStateDetectionSupport = new MachineStateDetectionSupport();

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

    private static SmartThingsDeviceStatusResDto washerStatusWithSwitch(String machineState,
            String jobState,
            String switchState) {
        var washerOpState = new WasherOperatingState(attr(machineState), attr(jobState), null);
        var switchCapability = new SwitchCapability(attr(switchState));
        return new SmartThingsDeviceStatusResDto(
                Map.of("main", new ComponentStatus(washerOpState, null, switchCapability, null)));
    }

    @Nested
    @DisplayName("세탁기 완료 판정")
    class WasherCompletion {

        @Test
        @DisplayName("jobState=finish, machineState=stop, 완료 시각이 지났으면 완료로 판정한다")
        void shouldComplete_WhenFinishedStoppedAndTimePassed() {
            var past = ZonedDateTime.now(ZoneId.of("Asia/Seoul")).minusMinutes(1);
            var status = washerStatus("stop", "finish", isoUtc(past));

            var result = machineStateDetectionSupport.isCompleted(status, WASHER);

            assertThat(result).isPresent();
        }

        @Test
        @DisplayName("jobState=finish 이지만 machineState=run 이면 조기 보고로 보고 미완료로 판정한다")
        void shouldNotComplete_WhenFinishedButStillRunning() {
            var past = ZonedDateTime.now(ZoneId.of("Asia/Seoul")).minusMinutes(1);
            var status = washerStatus("run", "finish", isoUtc(past));

            var result = machineStateDetectionSupport.isCompleted(status, WASHER);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("jobState=finish, machineState=stop 이지만 완료 시각이 미래(잔여시간 남음)면 미완료로 판정한다")
        void shouldNotComplete_WhenFinishedButCompletionTimeInFuture() {
            var future = ZonedDateTime.now(ZoneId.of("Asia/Seoul")).plusMinutes(3);
            var status = washerStatus("stop", "finish", isoUtc(future));

            var result = machineStateDetectionSupport.isCompleted(status, WASHER);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("jobState가 finish 가 아니면 미완료로 판정한다")
        void shouldNotComplete_WhenJobStateNotFinished() {
            var status = washerStatus("run", "spin", null);

            var result = machineStateDetectionSupport.isCompleted(status, WASHER);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("completionTime 이 없어도 jobState=finish, machineState=stop 이면 완료로 판정한다")
        void shouldComplete_WhenCompletionTimeNull() {
            var status = washerStatus("stop", "finish", null);

            var result = machineStateDetectionSupport.isCompleted(status, WASHER);

            assertThat(result).isPresent();
        }

        @Test
        @DisplayName("완료 직후 jobState가 none으로 리셋되어도 완료 시각이 지났으면 완료로 판정한다")
        void shouldComplete_WhenJobStateResetAndCompletionTimePassed() {
            var past = ZonedDateTime.now(ZoneId.of("Asia/Seoul")).minusMinutes(1);
            var status = washerStatus("stop", "none", isoUtc(past));

            var result = machineStateDetectionSupport.isCompleted(status, WASHER);

            assertThat(result).isPresent();
        }

        @Test
        @DisplayName("jobState가 none으로 리셋됐지만 완료 시각이 없으면 완료로 판정하지 않는다")
        void shouldNotComplete_WhenJobStateResetAndCompletionTimeNull() {
            var status = washerStatus("stop", "none", null);

            var result = machineStateDetectionSupport.isCompleted(status, WASHER);

            assertThat(result).isEmpty();
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

            var result = machineStateDetectionSupport.isCompleted(status, DRYER);

            assertThat(result).isPresent();
        }

        @Test
        @DisplayName("jobState=cooling 잔여 단계이고 machineState=run 이면 미완료로 판정한다")
        void shouldNotComplete_WhenCoolingAndRunning() {
            var future = ZonedDateTime.now(ZoneId.of("Asia/Seoul")).plusMinutes(2);
            var status = dryerStatus("run", "cooling", isoUtc(future));

            var result = machineStateDetectionSupport.isCompleted(status, DRYER);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("건조 완료 직후 jobState가 none으로 리셋되어도 완료 시각이 지났으면 완료로 판정한다")
        void shouldComplete_WhenDryerJobStateResetAndCompletionTimePassed() {
            var past = ZonedDateTime.now(ZoneId.of("Asia/Seoul")).minusMinutes(1);
            var status = dryerStatus("stop", "none", isoUtc(past));

            var result = machineStateDetectionSupport.isCompleted(status, DRYER);

            assertThat(result).isPresent();
        }
    }

    @Nested
    @DisplayName("비정상 중단 판정")
    class Interruption {

        @Test
        @DisplayName("전원이 꺼져 있으면(switch=off) 중단으로 판정한다")
        void shouldInterrupt_WhenPowerOff() {
            var status = washerStatusWithSwitch("run", "wash", "off");

            assertThat(machineStateDetectionSupport.isInterrupted(status, WASHER)).isTrue();
        }

        @Test
        @DisplayName("세탁기가 진행 단계(spin) 도중 stop 되면 중단으로 판정한다")
        void shouldInterrupt_WhenWasherStoppedMidCycle() {
            var status = washerStatus("stop", "spin", null);

            assertThat(machineStateDetectionSupport.isInterrupted(status, WASHER)).isTrue();
        }

        @Test
        @DisplayName("세탁기가 stop, jobState=finish(정상 완료)면 중단으로 판정하지 않는다")
        void shouldNotInterrupt_WhenWasherFinished() {
            var status = washerStatus("stop", "finish", null);

            assertThat(machineStateDetectionSupport.isInterrupted(status, WASHER)).isFalse();
        }

        @Test
        @DisplayName("세탁기가 stop, jobState=none(종료 직후 리셋·유휴)이면 중단으로 판정하지 않는다")
        void shouldNotInterrupt_WhenWasherStoppedAndJobReset() {
            var status = washerStatus("stop", "none", null);

            assertThat(machineStateDetectionSupport.isInterrupted(status, WASHER)).isFalse();
        }

        @Test
        @DisplayName("세탁기가 stop, jobState=null이면 중단으로 판정하지 않는다")
        void shouldNotInterrupt_WhenWasherStoppedAndJobNull() {
            var status = washerStatus("stop", null, null);

            assertThat(machineStateDetectionSupport.isInterrupted(status, WASHER)).isFalse();
        }

        @Test
        @DisplayName("세탁기가 run 중이면 중단으로 판정하지 않는다")
        void shouldNotInterrupt_WhenWasherRunning() {
            var status = washerStatus("run", "wash", null);

            assertThat(machineStateDetectionSupport.isInterrupted(status, WASHER)).isFalse();
        }

        @Test
        @DisplayName("건조기가 진행 단계(drying) 도중 stop 되면 중단으로 판정한다")
        void shouldInterrupt_WhenDryerStoppedMidCycle() {
            var status = dryerStatus("stop", "drying", null);

            assertThat(machineStateDetectionSupport.isInterrupted(status, DRYER)).isTrue();
        }

        @Test
        @DisplayName("건조기가 stop, jobState=finished(정상 완료)면 중단으로 판정하지 않는다")
        void shouldNotInterrupt_WhenDryerFinished() {
            var status = dryerStatus("stop", "finished", null);

            assertThat(machineStateDetectionSupport.isInterrupted(status, DRYER)).isFalse();
        }

        @Test
        @DisplayName("세탁기 capability만 보므로 건조기가 작동 중인 세탁기 기기를 중단으로 오판하지 않는다")
        void shouldNotInterrupt_WhenOtherTypeIdle() {
            // 세탁기가 정상 작동(run) 중이지만 건조기 capability가 함께 노출되어 stop/none인 경우에도,
            // 세탁기 타입 판정은 세탁기 capability만 확인해야 한다.
            var washerOpState = new WasherOperatingState(attr("run"), attr("wash"), null);
            var dryerOpState = new DryerOperatingState(attr("stop"), attr("none"), null);
            var status = new SmartThingsDeviceStatusResDto(
                    Map.of("main", new ComponentStatus(washerOpState, dryerOpState, null, null)));

            assertThat(machineStateDetectionSupport.isInterrupted(status, WASHER)).isFalse();
        }
    }

    @Nested
    @DisplayName("일시정지 판정")
    class Paused {

        @Test
        @DisplayName("machineState=pause 이면 일시정지로 판정한다")
        void shouldPause_WhenMachineStatePause() {
            var status = washerStatus("pause", "wash", null);

            assertThat(machineStateDetectionSupport.isPaused(status, WASHER)).isTrue();
        }

        @Test
        @DisplayName("machineState=run 이면 일시정지가 아니다")
        void shouldNotPause_WhenRunning() {
            var status = dryerStatus("run", "drying", null);

            assertThat(machineStateDetectionSupport.isPaused(status, DRYER)).isFalse();
        }
    }

    @Nested
    @DisplayName("작동 판정")
    class Running {

        @Test
        @DisplayName("machineState=run 이면 작동 중으로 판정한다")
        void shouldRun_WhenMachineStateRun() {
            var status = washerStatus("run", "wash", null);

            assertThat(machineStateDetectionSupport.isRunning(status, WASHER)).isTrue();
        }

        @Test
        @DisplayName("machineState=stop 이면 작동 중이 아니다")
        void shouldNotRun_WhenStopped() {
            var status = washerStatus("stop", "none", null);

            assertThat(machineStateDetectionSupport.isRunning(status, WASHER)).isFalse();
        }
    }
}
