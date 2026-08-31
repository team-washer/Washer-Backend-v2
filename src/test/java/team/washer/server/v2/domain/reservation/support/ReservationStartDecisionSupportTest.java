package team.washer.server.v2.domain.reservation.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import team.washer.server.v2.domain.reservation.support.ReservationStartDecisionSupport.StartDecision;
import team.washer.server.v2.domain.smartthings.dto.response.SmartThingsDeviceStatusResDto;
import team.washer.server.v2.domain.smartthings.dto.response.SmartThingsDeviceStatusResDto.AttributeState;
import team.washer.server.v2.domain.smartthings.dto.response.SmartThingsDeviceStatusResDto.ComponentStatus;
import team.washer.server.v2.domain.smartthings.dto.response.SmartThingsDeviceStatusResDto.DryerOperatingState;
import team.washer.server.v2.domain.smartthings.dto.response.SmartThingsDeviceStatusResDto.SwitchCapability;
import team.washer.server.v2.domain.smartthings.dto.response.SmartThingsDeviceStatusResDto.WasherOperatingState;
import team.washer.server.v2.domain.smartthings.support.MachineStateDetectionSupport;

@DisplayName("ReservationStartDecisionSupport 시작 판정")
class ReservationStartDecisionSupportTest {

    private static final boolean WASHER = true;
    private static final boolean DRYER = false;

    private final ReservationStartDecisionSupport reservationStartDecisionSupport = new ReservationStartDecisionSupport(
            new MachineStateDetectionSupport());

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
    @DisplayName("시작 신호가 있으면")
    class Started {

        @Test
        @DisplayName("machineState=run이면 STARTED를 반환한다")
        void shouldStart_WhenMachineStateRun() {
            var status = washerStatus("run", "wash", null);

            assertThat(reservationStartDecisionSupport.decide(status, WASHER)).isEqualTo(StartDecision.STARTED);
        }

        @Test
        @DisplayName("machineState=pause이면 이미 실제 사이클이 시작된 것으로 보고 STARTED를 반환한다")
        void shouldStart_WhenMachineStatePause() {
            var status = washerStatus("pause", null, null);

            assertThat(reservationStartDecisionSupport.decide(status, WASHER)).isEqualTo(StartDecision.STARTED);
        }

        @Test
        @DisplayName("세탁기 active jobState가 있으면 STARTED를 반환한다")
        void shouldStartWasher_WhenActiveJobStateExists() {
            var status = washerStatus(null, "spin", null);

            assertThat(reservationStartDecisionSupport.decide(status, WASHER)).isEqualTo(StartDecision.STARTED);
        }

        @Test
        @DisplayName("machineState=stop이어도 active jobState가 있으면 STARTED를 반환한다")
        void shouldStart_WhenStoppedButActiveJobStateExists() {
            var status = washerStatus("stop", "wash", null);

            assertThat(reservationStartDecisionSupport.decide(status, WASHER)).isEqualTo(StartDecision.STARTED);
        }

        @Test
        @DisplayName("건조기 active jobState가 있으면 STARTED를 반환한다")
        void shouldStartDryer_WhenActiveJobStateExists() {
            var status = dryerStatus(null, "drying", null);

            assertThat(reservationStartDecisionSupport.decide(status, DRYER)).isEqualTo(StartDecision.STARTED);
        }

        @Test
        @DisplayName("미래 completionTime이 있으면 STARTED를 반환한다")
        void shouldStart_WhenFutureCompletionTimeExists() {
            var future = ZonedDateTime.now(ZoneId.of("Asia/Seoul")).plusMinutes(10);
            var status = washerStatus(null, null, isoUtc(future));

            assertThat(reservationStartDecisionSupport.decide(status, WASHER)).isEqualTo(StartDecision.STARTED);
        }
    }

    @Nested
    @DisplayName("명확한 미사용 신호가 있으면")
    class Idle {

        @Test
        @DisplayName("machineState=stop이면 IDLE을 반환한다")
        void shouldIdle_WhenMachineStateStop() {
            var status = washerStatus("stop", "none", null);

            assertThat(reservationStartDecisionSupport.decide(status, WASHER)).isEqualTo(StartDecision.IDLE);
        }

        @Test
        @DisplayName("switch=off이면 machineState=run이어도 IDLE을 반환한다")
        void shouldIdle_WhenSwitchOffEvenWithMachineStateRun() {
            var status = washerStatusWithSwitch("run", "wash", "off");

            assertThat(reservationStartDecisionSupport.decide(status, WASHER)).isEqualTo(StartDecision.IDLE);
        }
    }

    @Nested
    @DisplayName("판정을 확신할 수 없으면")
    class Unknown {

        @Test
        @DisplayName("상태 응답이 없으면 UNKNOWN을 반환한다")
        void shouldUnknown_WhenStatusNull() {
            assertThat(reservationStartDecisionSupport.decide(null, WASHER)).isEqualTo(StartDecision.UNKNOWN);
        }

        @Test
        @DisplayName("기기 상태와 보조 신호가 모두 없으면 UNKNOWN을 반환한다")
        void shouldUnknown_WhenNoSignalsExist() {
            var status = washerStatus(null, null, null);

            assertThat(reservationStartDecisionSupport.decide(status, WASHER)).isEqualTo(StartDecision.UNKNOWN);
        }
    }
}
