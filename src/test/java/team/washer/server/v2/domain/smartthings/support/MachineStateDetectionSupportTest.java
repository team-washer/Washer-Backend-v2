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
import team.washer.server.v2.global.util.DateTimeUtil;

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

    private static SmartThingsDeviceStatusResDto dryerStatusWithSwitch(String machineState,
            String jobState,
            String switchState) {
        var dryerOpState = new DryerOperatingState(attr(machineState), attr(jobState), null);
        var switchCapability = new SwitchCapability(attr(switchState));
        return new SmartThingsDeviceStatusResDto(
                Map.of("main", new ComponentStatus(null, dryerOpState, switchCapability, null)));
    }

    private static SmartThingsDeviceStatusResDto washerStatusWithTimestamps(String machineState,
            String machineStateTimestamp,
            String jobState,
            String jobStateTimestamp) {
        var washerOpState = new WasherOperatingState(new AttributeState(machineState, machineStateTimestamp, null),
                new AttributeState(jobState, jobStateTimestamp, null),
                null);
        return new SmartThingsDeviceStatusResDto(Map.of("main", new ComponentStatus(washerOpState, null, null, null)));
    }

    @Nested
    @DisplayName("세탁기 완료 판정")
    class WasherCompletion {

        @Test
        @DisplayName("jobState=finish, machineState=stop 이면 완료 시각이 미래여도 완료로 판정한다")
        void shouldComplete_WhenFinishedAndStopped() {
            var future = ZonedDateTime.now(ZoneId.of("Asia/Seoul")).plusMinutes(3);
            var status = washerStatus("stop", "finish", isoUtc(future));

            assertThat(machineStateDetectionSupport.isCompleted(status, WASHER)).isTrue();
        }

        @Test
        @DisplayName("jobState=finish 이면 machineState=run 이어도 완료로 판정한다")
        void shouldComplete_WhenFinishedButRunning() {
            var status = washerStatus("run", "finish", null);

            assertThat(machineStateDetectionSupport.isCompleted(status, WASHER)).isTrue();
        }

        @Test
        @DisplayName("jobState가 진행 단계(spin)이면 미완료로 판정한다")
        void shouldNotComplete_WhenJobActive() {
            var status = washerStatus("run", "spin", null);

            assertThat(machineStateDetectionSupport.isCompleted(status, WASHER)).isFalse();
        }

        @Test
        @DisplayName("machineState=stop, jobState=none 이면 완료 신호를 놓친 것으로 보고 완료로 판정한다")
        void shouldComplete_WhenStoppedWithJobReset() {
            var future = ZonedDateTime.now(ZoneId.of("Asia/Seoul")).plusMinutes(60);
            var status = washerStatus("stop", "none", isoUtc(future));

            assertThat(machineStateDetectionSupport.isCompleted(status, WASHER)).isTrue();
        }

        @Test
        @DisplayName("machineState=stop, jobState가 null이어도 완료로 판정한다")
        void shouldComplete_WhenStoppedWithNullJobState() {
            var status = washerStatus("stop", null, null);

            assertThat(machineStateDetectionSupport.isCompleted(status, WASHER)).isTrue();
        }

        @Test
        @DisplayName("machineState=run, jobState=none 이면 미완료로 판정한다")
        void shouldNotComplete_WhenRunningWithJobReset() {
            var status = washerStatus("run", "none", null);

            assertThat(machineStateDetectionSupport.isCompleted(status, WASHER)).isFalse();
        }

        @Test
        @DisplayName("진행 단계(wash) 도중 stop 되면 완료가 아니다")
        void shouldNotComplete_WhenStoppedMidCycle() {
            var status = washerStatus("stop", "wash", null);

            assertThat(machineStateDetectionSupport.isCompleted(status, WASHER)).isFalse();
        }

        @Test
        @DisplayName("전원이 꺼진 정지(jobState=none)는 중단일 수 있으므로 완료가 아니다")
        void shouldNotComplete_WhenPoweredOffWithJobReset() {
            var status = washerStatusWithSwitch("stop", "none", "off");

            assertThat(machineStateDetectionSupport.isCompleted(status, WASHER)).isFalse();
        }

        @Test
        @DisplayName("상태가 null이면 미완료로 판정한다")
        void shouldNotComplete_WhenStatusNull() {
            assertThat(machineStateDetectionSupport.isCompleted(null, WASHER)).isFalse();
        }
    }

    @Nested
    @DisplayName("건조기 완료 판정")
    class DryerCompletion {

        @Test
        @DisplayName("jobState=finished 이면 구김방지로 machineState=run 이어도 완료로 판정한다")
        void shouldComplete_WhenFinishedDuringWrinklePrevent() {
            var status = dryerStatus("run", "finished", null);

            assertThat(machineStateDetectionSupport.isCompleted(status, DRYER)).isTrue();
        }

        @Test
        @DisplayName("jobState=cooling 잔여 단계이고 machineState=run 이면 미완료로 판정한다")
        void shouldNotComplete_WhenCooling() {
            var status = dryerStatus("run", "cooling", null);

            assertThat(machineStateDetectionSupport.isCompleted(status, DRYER)).isFalse();
        }

        @Test
        @DisplayName("machineState=stop, jobState=none 이면 완료로 판정한다")
        void shouldComplete_WhenStoppedWithJobReset() {
            var status = dryerStatus("stop", "none", null);

            assertThat(machineStateDetectionSupport.isCompleted(status, DRYER)).isTrue();
        }

        @Test
        @DisplayName("전원이 꺼진 상태라도 jobState=finished 이면 완료로 판정한다")
        void shouldComplete_WhenFinishedAndPoweredOff() {
            var status = dryerStatusWithSwitch("stop", "finished", "off");

            assertThat(machineStateDetectionSupport.isCompleted(status, DRYER)).isTrue();
        }

        @Test
        @DisplayName("세탁기의 finish 값은 건조기 완료로 보지 않는다")
        void shouldNotComplete_WhenWasherFinishValue() {
            var status = dryerStatus("run", "finish", null);

            assertThat(machineStateDetectionSupport.isCompleted(status, DRYER)).isFalse();
        }
    }

    @Nested
    @DisplayName("완료 신호 갱신 시각")
    class CompletionSignalTimestamp {

        private static final String MACHINE_STATE_TIMESTAMP = "2026-09-18T01:00:00Z";
        private static final String JOB_STATE_TIMESTAMP = "2026-09-18T02:00:00Z";

        @Test
        @DisplayName("jobState 완료 신호면 jobState 갱신 시각을 반환한다")
        void shouldReturnJobStateTimestamp_WhenFinished() {
            var status = washerStatusWithTimestamps("run", MACHINE_STATE_TIMESTAMP, "finish", JOB_STATE_TIMESTAMP);

            var result = machineStateDetectionSupport.resolveCompletionSignalTimestamp(status, WASHER);

            assertThat(result).contains(DateTimeUtil.parseAndConvertToKoreaTime(JOB_STATE_TIMESTAMP));
        }

        @Test
        @DisplayName("정지 신호면 machineState 갱신 시각을 반환한다")
        void shouldReturnMachineStateTimestamp_WhenStoppedWithJobReset() {
            var status = washerStatusWithTimestamps("stop", MACHINE_STATE_TIMESTAMP, "none", JOB_STATE_TIMESTAMP);

            var result = machineStateDetectionSupport.resolveCompletionSignalTimestamp(status, WASHER);

            assertThat(result).contains(DateTimeUtil.parseAndConvertToKoreaTime(MACHINE_STATE_TIMESTAMP));
        }

        @Test
        @DisplayName("갱신 시각이 없으면 빈 값을 반환한다")
        void shouldReturnEmpty_WhenTimestampMissing() {
            var status = washerStatus("stop", "finish", null);

            assertThat(machineStateDetectionSupport.resolveCompletionSignalTimestamp(status, WASHER)).isEmpty();
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
