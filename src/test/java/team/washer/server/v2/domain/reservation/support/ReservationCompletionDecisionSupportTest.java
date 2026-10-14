package team.washer.server.v2.domain.reservation.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import team.washer.server.v2.domain.reservation.entity.Reservation;
import team.washer.server.v2.domain.reservation.enums.ReservationStatus;
import team.washer.server.v2.domain.smartthings.dto.response.SmartThingsDeviceStatusResDto;
import team.washer.server.v2.domain.smartthings.dto.response.SmartThingsDeviceStatusResDto.AttributeState;
import team.washer.server.v2.domain.smartthings.dto.response.SmartThingsDeviceStatusResDto.ComponentStatus;
import team.washer.server.v2.domain.smartthings.dto.response.SmartThingsDeviceStatusResDto.DryerOperatingState;
import team.washer.server.v2.domain.smartthings.dto.response.SmartThingsDeviceStatusResDto.SwitchCapability;
import team.washer.server.v2.domain.smartthings.dto.response.SmartThingsDeviceStatusResDto.WasherOperatingState;
import team.washer.server.v2.domain.smartthings.support.MachineStateDetectionSupport;
import team.washer.server.v2.global.util.DateTimeUtil;

@DisplayName("ReservationCompletionDecisionSupport 완료 판정")
class ReservationCompletionDecisionSupportTest {

    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");
    private static final boolean WASHER = true;
    private static final boolean DRYER = false;

    private final ReservationCompletionDecisionSupport completionDecisionSupport = new ReservationCompletionDecisionSupport(
            new MachineStateDetectionSupport());

    private static String isoUtc(LocalDateTime koreaTime) {
        return koreaTime.atZone(KOREA_ZONE).withZoneSameInstant(ZoneId.of("UTC")).toLocalDateTime().toString() + "Z";
    }

    private static AttributeState attr(String value, LocalDateTime timestamp) {
        return new AttributeState(value, timestamp == null ? null : isoUtc(timestamp), null);
    }

    private static SmartThingsDeviceStatusResDto washerStatus(String machineState,
            LocalDateTime machineStateUpdatedAt,
            String jobState,
            LocalDateTime jobStateUpdatedAt) {
        var washerOpState = new WasherOperatingState(attr(machineState, machineStateUpdatedAt),
                attr(jobState, jobStateUpdatedAt),
                null);
        return new SmartThingsDeviceStatusResDto(Map.of("main", new ComponentStatus(washerOpState, null, null, null)));
    }

    private static SmartThingsDeviceStatusResDto dryerStatus(String machineState,
            LocalDateTime machineStateUpdatedAt,
            String jobState,
            LocalDateTime jobStateUpdatedAt) {
        var dryerOpState = new DryerOperatingState(attr(machineState, machineStateUpdatedAt),
                attr(jobState, jobStateUpdatedAt),
                null);
        return new SmartThingsDeviceStatusResDto(Map.of("main", new ComponentStatus(null, dryerOpState, null, null)));
    }

    private static SmartThingsDeviceStatusResDto poweredOffWasherStatus(String machineState,
            LocalDateTime machineStateUpdatedAt,
            String jobState) {
        var washerOpState = new WasherOperatingState(attr(machineState, machineStateUpdatedAt),
                attr(jobState, machineStateUpdatedAt),
                null);
        return new SmartThingsDeviceStatusResDto(Map.of("main",
                new ComponentStatus(washerOpState, null, new SwitchCapability(attr("off", null)), null)));
    }

    private static SmartThingsDeviceStatusResDto poweredOffDryerStatus(String machineState,
            LocalDateTime machineStateUpdatedAt,
            String jobState) {
        var dryerOpState = new DryerOperatingState(attr(machineState, machineStateUpdatedAt),
                attr(jobState, machineStateUpdatedAt),
                null);
        return new SmartThingsDeviceStatusResDto(
                Map.of("main", new ComponentStatus(null, dryerOpState, new SwitchCapability(attr("off", null)), null)));
    }

    private static Reservation runningReservation(LocalDateTime startTime, LocalDateTime expectedCompletionTime) {
        return Reservation.builder().status(ReservationStatus.RUNNING).startTime(startTime)
                .expectedCompletionTime(expectedCompletionTime).build();
    }

    private static Reservation pausedReservation(LocalDateTime startTime,
            LocalDateTime expectedCompletionTime,
            LocalDateTime pausedAt) {
        return Reservation.builder().status(ReservationStatus.RUNNING).startTime(startTime)
                .expectedCompletionTime(expectedCompletionTime).pausedAt(pausedAt).build();
    }

    @Nested
    @DisplayName("완료 신호가 있을 때")
    class WithCompletionSignal {

        @Test
        @DisplayName("세탁기 jobState=finish 이면 예상 완료 시각보다 이르더라도 즉시 완료로 판정한다")
        void shouldComplete_WhenWasherFinishedEarly() {
            // Given
            var now = DateTimeUtil.nowInKorea();
            var reservation = runningReservation(now.minusMinutes(40), now.plusMinutes(10));
            var status = washerStatus("run", now.minusMinutes(40), "finish", now.minusSeconds(10));

            // When
            var decision = completionDecisionSupport.decide(reservation, status, WASHER);

            // Then
            assertThat(decision.isCompleted()).isTrue();
            assertThat(decision.reason()).isEqualTo("job_finished");
        }

        @Test
        @DisplayName("건조기가 구김방지로 machineState=run 이어도 jobState=finished 이면 완료로 판정한다")
        void shouldComplete_WhenDryerFinishedDuringWrinklePrevent() {
            // Given
            var now = DateTimeUtil.nowInKorea();
            var reservation = runningReservation(now.minusMinutes(60), now.plusMinutes(3));
            var status = dryerStatus("run", now.minusMinutes(61), "finished", now.minusMinutes(1));

            // When
            var decision = completionDecisionSupport.decide(reservation, status, DRYER);

            // Then
            assertThat(decision.isCompleted()).isTrue();
            assertThat(decision.reason()).isEqualTo("job_finished");
        }

        @Test
        @DisplayName("정지 후 jobState=none 으로 리셋된 경우 완료로 판정한다")
        void shouldComplete_WhenStoppedWithJobReset() {
            // Given
            var now = DateTimeUtil.nowInKorea();
            var reservation = runningReservation(now.minusMinutes(60), null);
            var status = washerStatus("stop", now.minusMinutes(1), "none", now.minusMinutes(1));

            // When
            var decision = completionDecisionSupport.decide(reservation, status, WASHER);

            // Then
            assertThat(decision.isCompleted()).isTrue();
            assertThat(decision.reason()).isEqualTo("stopped_idle");
        }

        @Test
        @DisplayName("갱신 시각이 없으면 이전 사이클 여부를 알 수 없으므로 완료로 판정한다")
        void shouldComplete_WhenTimestampMissing() {
            // Given
            var now = DateTimeUtil.nowInKorea();
            var reservation = runningReservation(now.minusMinutes(60), null);
            var status = washerStatus("stop", null, "finish", null);

            // When
            var decision = completionDecisionSupport.decide(reservation, status, WASHER);

            // Then
            assertThat(decision.isCompleted()).isTrue();
        }
    }

    @Nested
    @DisplayName("이전 사이클 신호일 때")
    class WithStaleSignal {

        @Test
        @DisplayName("jobState=finish 갱신 시각이 예약 시작 전이면 보류한다")
        void shouldDefer_WhenFinishedBeforeStart() {
            // Given
            var now = DateTimeUtil.nowInKorea();
            var reservation = runningReservation(now.minusMinutes(1), null);
            var status = washerStatus("stop", now.minusMinutes(30), "finish", now.minusMinutes(30));

            // When
            var decision = completionDecisionSupport.decide(reservation, status, WASHER);

            // Then
            assertThat(decision.isDeferred()).isTrue();
            assertThat(decision.reason()).isEqualTo("stale_completion");
        }

        @Test
        @DisplayName("정지 신호의 machineState 갱신 시각이 예약 시작 전이면 보류한다")
        void shouldDefer_WhenStoppedBeforeStart() {
            // Given
            var now = DateTimeUtil.nowInKorea();
            var reservation = runningReservation(now.minusMinutes(1), null);
            var status = washerStatus("stop", now.minusMinutes(30), "none", now.minusSeconds(10));

            // When
            var decision = completionDecisionSupport.decide(reservation, status, WASHER);

            // Then
            assertThat(decision.isDeferred()).isTrue();
        }

        @Test
        @DisplayName("jobState 완료 신호는 machineState 갱신 시각이 예약 시작 전이어도 이번 사이클 신호로 본다")
        void shouldComplete_WhenOnlyMachineStateIsOld() {
            // Given
            var now = DateTimeUtil.nowInKorea();
            var reservation = runningReservation(now.minusMinutes(50), null);
            var status = dryerStatus("run", now.minusMinutes(51), "finished", now.minusSeconds(30));

            // When
            var decision = completionDecisionSupport.decide(reservation, status, DRYER);

            // Then
            assertThat(decision.isCompleted()).isTrue();
        }
    }

    @Nested
    @DisplayName("완료 신호가 없을 때")
    class WithoutCompletionSignal {

        @Test
        @DisplayName("진행 중이면 미완료로 판정한다")
        void shouldNotComplete_WhenRunning() {
            // Given
            var now = DateTimeUtil.nowInKorea();
            var reservation = runningReservation(now.minusMinutes(30), now.minusMinutes(1));
            var status = washerStatus("run", now.minusMinutes(30), "rinse", now.minusMinutes(5));

            // When
            var decision = completionDecisionSupport.decide(reservation, status, WASHER);

            // Then
            assertThat(decision.isCompleted()).isFalse();
            assertThat(decision.isDeferred()).isFalse();
        }

        @Test
        @DisplayName("완료 예정 시각이 지난 정지라도 진행 단계 jobState면 완료로 보지 않는다")
        void shouldNotComplete_WhenStoppedMidCycleAfterExpectedTime() {
            // Given
            var now = DateTimeUtil.nowInKorea();
            var reservation = runningReservation(now.minusMinutes(60), now.minusMinutes(1));
            var status = washerStatus("stop", now.minusSeconds(30), "spin", now.minusMinutes(3));

            // When
            var decision = completionDecisionSupport.decide(reservation, status, WASHER);

            // Then
            assertThat(decision.isCompleted()).isFalse();
            assertThat(decision.isCandidateRejected()).isFalse();
        }
    }

    @Nested
    @DisplayName("완료 신호 없이 전원이 꺼진 정지일 때")
    class WithPoweredOffIdleStop {

        @Test
        @DisplayName("세탁기가 완료 예정 시각 이후에 멈췄어도 완료로 확정하지 않고 완료 예정 무렵 정지로 분류한다")
        void shouldClassifyNearCompletion_WhenWasherStoppedAfterExpectedCompletion() {
            // Given
            var now = DateTimeUtil.nowInKorea();
            var reservation = runningReservation(now.minusMinutes(50), now.minusMinutes(3));
            var status = poweredOffWasherStatus("stop", now.minusMinutes(2), "none");

            // When
            var decision = completionDecisionSupport.decide(reservation, status, WASHER);

            // Then
            assertThat(decision.isCompleted()).isFalse();
            assertThat(decision.isDeferred()).isFalse();
            assertThat(decision.isCandidateRejected()).isTrue();
            assertThat(decision.reason()).isEqualTo("power_off_near_expected_completion");
        }

        @Test
        @DisplayName("건조기가 완료 예정 시각보다 분류 범위 이내로 일찍 멈췄으면 완료 예정 무렵 정지로 분류한다")
        void shouldClassifyNearCompletion_WhenDryerStoppedWithinWindow() {
            // Given
            var now = DateTimeUtil.nowInKorea();
            var reservation = runningReservation(now.minusMinutes(60), now.plusMinutes(2));
            var status = poweredOffDryerStatus("stop", now.minusMinutes(1), null);

            // When
            var decision = completionDecisionSupport.decide(reservation, status, DRYER);

            // Then
            assertThat(decision.isCompleted()).isFalse();
            assertThat(decision.reason()).isEqualTo("power_off_near_expected_completion");
        }

        @Test
        @DisplayName("완료 예정 시각보다 훨씬 일찍 멈췄으면 완료로 보지 않고 기각 사유를 남긴다")
        void shouldReject_WhenStoppedLongBeforeExpectedCompletion() {
            // Given
            var now = DateTimeUtil.nowInKorea();
            var reservation = runningReservation(now.minusMinutes(20), now.plusMinutes(30));
            var status = poweredOffWasherStatus("stop", now.minusMinutes(1), "none");

            // When
            var decision = completionDecisionSupport.decide(reservation, status, WASHER);

            // Then
            assertThat(decision.isCompleted()).isFalse();
            assertThat(decision.isDeferred()).isFalse();
            assertThat(decision.isCandidateRejected()).isTrue();
            assertThat(decision.reason()).isEqualTo("power_off_before_expected_completion");
        }

        @Test
        @DisplayName("감지가 늦어 현재 시각이 완료 예정 시각을 지났더라도 멈춘 시각이 일렀으면 완료로 보지 않는다")
        void shouldReject_WhenDetectedLateButStoppedEarly() {
            // Given
            var now = DateTimeUtil.nowInKorea();
            var reservation = runningReservation(now.minusMinutes(70), now.minusMinutes(10));
            var status = poweredOffWasherStatus("stop", now.minusMinutes(40), "none");

            // When
            var decision = completionDecisionSupport.decide(reservation, status, WASHER);

            // Then
            assertThat(decision.isCompleted()).isFalse();
            assertThat(decision.reason()).isEqualTo("power_off_before_expected_completion");
        }

        @Test
        @DisplayName("일시정지된 채로 꺼졌으면 멈춘 시각이 완료 예정 무렵이어도 일시정지 중 전원 꺼짐으로 분류한다")
        void shouldClassifyPaused_WhenPausedLongBeforeExpectedCompletion() {
            // Given
            var now = DateTimeUtil.nowInKorea();
            var reservation = pausedReservation(now.minusMinutes(50), now.plusMinutes(2), now.minusMinutes(9));
            var status = poweredOffWasherStatus("stop", now.minusMinutes(1), "none");

            // When
            var decision = completionDecisionSupport.decide(reservation, status, WASHER);

            // Then
            assertThat(decision.isCompleted()).isFalse();
            assertThat(decision.isCandidateRejected()).isTrue();
            assertThat(decision.reason()).isEqualTo("power_off_while_paused");
        }

        @Test
        @DisplayName("일시정지가 늦게 관측돼 기록된 시각이 완료 예정 무렵이어도 일시정지 중 전원 꺼짐으로 분류한다")
        void shouldClassifyPaused_WhenPauseObservedLate() {
            // Given
            var now = DateTimeUtil.nowInKorea();
            var reservation = pausedReservation(now.minusMinutes(50), now.plusMinutes(2), now.minusMinutes(2));
            var status = poweredOffDryerStatus("stop", now.minusMinutes(1), "none");

            // When
            var decision = completionDecisionSupport.decide(reservation, status, DRYER);

            // Then
            assertThat(decision.isCompleted()).isFalse();
            assertThat(decision.reason()).isEqualTo("power_off_while_paused");
        }

        @Test
        @DisplayName("일시정지 이력이 있어도 멈춘 시각 자체가 일렀으면 멈춘 시각 기준의 기각 사유를 남긴다")
        void shouldRejectByStopTime_WhenPausedAndStoppedEarly() {
            // Given
            var now = DateTimeUtil.nowInKorea();
            var reservation = pausedReservation(now.minusMinutes(20), now.plusMinutes(30), now.minusMinutes(5));
            var status = poweredOffWasherStatus("stop", now.minusMinutes(1), "none");

            // When
            var decision = completionDecisionSupport.decide(reservation, status, WASHER);

            // Then
            assertThat(decision.isCompleted()).isFalse();
            assertThat(decision.reason()).isEqualTo("power_off_before_expected_completion");
        }

        @Test
        @DisplayName("멈춘 시각을 알 수 없으면 완료로 보지 않는다")
        void shouldReject_WhenStopTimestampMissing() {
            // Given
            var now = DateTimeUtil.nowInKorea();
            var reservation = runningReservation(now.minusMinutes(50), now.minusMinutes(3));
            var status = poweredOffWasherStatus("stop", null, "none");

            // When
            var decision = completionDecisionSupport.decide(reservation, status, WASHER);

            // Then
            assertThat(decision.isCompleted()).isFalse();
            assertThat(decision.reason()).isEqualTo("power_off_without_completion_evidence");
        }

        @Test
        @DisplayName("예약에 완료 예정 시각이 없으면 완료로 보지 않는다")
        void shouldReject_WhenExpectedCompletionTimeMissing() {
            // Given
            var now = DateTimeUtil.nowInKorea();
            var reservation = runningReservation(now.minusMinutes(50), null);
            var status = poweredOffWasherStatus("stop", now.minusMinutes(2), "none");

            // When
            var decision = completionDecisionSupport.decide(reservation, status, WASHER);

            // Then
            assertThat(decision.isCompleted()).isFalse();
            assertThat(decision.reason()).isEqualTo("power_off_without_completion_evidence");
        }

        @Test
        @DisplayName("멈춘 시각이 예약 시작 전이면 이번 사이클의 근거가 아니므로 완료로 보지 않는다")
        void shouldReject_WhenStoppedBeforeStart() {
            // Given
            var now = DateTimeUtil.nowInKorea();
            var reservation = runningReservation(now.minusMinutes(2), now.plusMinutes(1));
            var status = poweredOffWasherStatus("stop", now.minusMinutes(3), "none");

            // When
            var decision = completionDecisionSupport.decide(reservation, status, WASHER);

            // Then
            assertThat(decision.isCompleted()).isFalse();
            assertThat(decision.reason()).isEqualTo("power_off_without_completion_evidence");
        }

        @Test
        @DisplayName("전원이 꺼졌어도 jobState가 진행 단계에 머물러 있으면 완료 후보로 검토하지 않는다")
        void shouldNotComplete_WhenPoweredOffMidCycleJobState() {
            // Given
            var now = DateTimeUtil.nowInKorea();
            var reservation = runningReservation(now.minusMinutes(50), now.minusMinutes(3));
            var status = poweredOffWasherStatus("stop", now.minusMinutes(2), "spin");

            // When
            var decision = completionDecisionSupport.decide(reservation, status, WASHER);

            // Then
            assertThat(decision.isCompleted()).isFalse();
            assertThat(decision.isCandidateRejected()).isFalse();
        }

        @Test
        @DisplayName("전원이 꺼졌어도 jobState=finish 이면 완료 예정 시각과 무관하게 완료로 판정한다")
        void shouldComplete_WhenPoweredOffWithFinishedJobState() {
            // Given
            var now = DateTimeUtil.nowInKorea();
            var reservation = runningReservation(now.minusMinutes(20), now.plusMinutes(30));
            var status = poweredOffWasherStatus("stop", now.minusMinutes(1), "finish");

            // When
            var decision = completionDecisionSupport.decide(reservation, status, WASHER);

            // Then
            assertThat(decision.isCompleted()).isTrue();
            assertThat(decision.reason()).isEqualTo("job_finished");
        }
    }
}
