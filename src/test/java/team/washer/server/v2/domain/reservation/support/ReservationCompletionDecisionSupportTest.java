package team.washer.server.v2.domain.reservation.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import team.washer.server.v2.domain.reservation.entity.Reservation;
import team.washer.server.v2.domain.smartthings.dto.response.SmartThingsDeviceStatusResDto;
import team.washer.server.v2.domain.smartthings.support.MachineStateDetectionSupport;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReservationCompletionDecisionSupport 완료 판정")
class ReservationCompletionDecisionSupportTest {

    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");

    @InjectMocks
    private ReservationCompletionDecisionSupport completionDecisionSupport;

    @Mock
    private MachineStateDetectionSupport machineStateDetectionSupport;

    @Mock
    private Reservation reservation;

    private SmartThingsDeviceStatusResDto buildWasherStatus(String machineState,
            String jobState,
            String completionTime) {
        return buildWasherStatus(machineState, null, jobState, null, completionTime);
    }

    private SmartThingsDeviceStatusResDto buildWasherStatus(String machineState,
            String machineStateTimestamp,
            String jobState,
            String jobStateTimestamp,
            String completionTime) {
        var machineStateAttr = new SmartThingsDeviceStatusResDto.AttributeState(machineState,
                machineStateTimestamp,
                null);
        var jobStateAttr = new SmartThingsDeviceStatusResDto.AttributeState(jobState, jobStateTimestamp, null);
        var completionTimeAttr = new SmartThingsDeviceStatusResDto.AttributeState(completionTime, null, null);
        var washerOpState = new SmartThingsDeviceStatusResDto.WasherOperatingState(machineStateAttr,
                jobStateAttr,
                completionTimeAttr);
        var componentStatus = new SmartThingsDeviceStatusResDto.ComponentStatus(washerOpState, null, null, null);
        return new SmartThingsDeviceStatusResDto(Map.of("main", componentStatus));
    }

    private SmartThingsDeviceStatusResDto buildDryerStatus(String machineState,
            String jobState,
            String completionTime) {
        var machineStateAttr = new SmartThingsDeviceStatusResDto.AttributeState(machineState, null, null);
        var jobStateAttr = new SmartThingsDeviceStatusResDto.AttributeState(jobState, null, null);
        var completionTimeAttr = new SmartThingsDeviceStatusResDto.AttributeState(completionTime, null, null);
        var dryerOpState = new SmartThingsDeviceStatusResDto.DryerOperatingState(machineStateAttr,
                jobStateAttr,
                completionTimeAttr);
        var componentStatus = new SmartThingsDeviceStatusResDto.ComponentStatus(null, dryerOpState, null, null);
        return new SmartThingsDeviceStatusResDto(Map.of("main", componentStatus));
    }

    private String isoUtc(LocalDateTime koreaTime) {
        return koreaTime.atZone(KOREA_ZONE).withZoneSameInstant(ZoneId.of("UTC")).toLocalDateTime().toString() + "Z";
    }

    private void givenDetectedCompletion(LocalDateTime completionTime) {
        when(machineStateDetectionSupport.isCompleted(any(), anyBoolean())).thenReturn(Optional.of(completionTime));
    }

    private void givenNoDetectedCompletion() {
        when(machineStateDetectionSupport.isCompleted(any(), anyBoolean())).thenReturn(Optional.empty());
    }

    @Nested
    @DisplayName("SmartThings 완료 보고 경로")
    class SmartThingsCompletedPathTest {

        @Test
        @DisplayName("가드를 모두 통과하면 완료로 판정한다")
        void shouldReturnCompleted_WhenAllGuardsPassed() {
            // Given
            var nowKst = LocalDateTime.now(KOREA_ZONE);
            var status = buildWasherStatus("stop", "finish", isoUtc(nowKst));
            when(reservation.getStartTime()).thenReturn(nowKst.minusMinutes(60));
            when(reservation.getExpectedCompletionTime()).thenReturn(nowKst);
            givenDetectedCompletion(nowKst);

            // When
            var decision = completionDecisionSupport.decide(reservation, status, true);

            // Then
            assertThat(decision.isCompleted()).isTrue();
            assertThat(decision.reason()).isEqualTo("smartthings_completed");
            assertThat(decision.completionTime()).isEqualTo(nowKst);
        }

        @Test
        @DisplayName("완료 시각이 예약 시작 시각보다 이전이면 이전 사이클의 잔재로 보고 보류한다")
        void shouldDefer_WhenCompletionTimeBeforeStartTime() {
            // Given
            var startTime = LocalDateTime.now(KOREA_ZONE);
            var staleCompletionTime = startTime.minusMinutes(5);
            var status = buildWasherStatus("stop", "finish", isoUtc(staleCompletionTime));
            when(reservation.getStartTime()).thenReturn(startTime);
            givenDetectedCompletion(staleCompletionTime);

            // When
            var decision = completionDecisionSupport.decide(reservation, status, true);

            // Then
            assertThat(decision.isDeferred()).isTrue();
            assertThat(decision.reason()).isEqualTo("stale_completion");
        }

        @Test
        @DisplayName("완료 신호의 갱신 시각이 예약 시작 전이면 이전 사이클의 잔재로 보고 보류한다")
        void shouldDefer_WhenCompletionSignalTimestampBeforeStartTime() {
            // Given
            var startTime = LocalDateTime.now(KOREA_ZONE);
            var staleTimestamp = isoUtc(startTime.minusMinutes(1));
            var status = buildWasherStatus("stop", staleTimestamp, "finish", staleTimestamp, isoUtc(startTime));
            when(reservation.getStartTime()).thenReturn(startTime);
            givenDetectedCompletion(startTime.plusMinutes(1));

            // When
            var decision = completionDecisionSupport.decide(reservation, status, true);

            // Then
            assertThat(decision.isDeferred()).isTrue();
            assertThat(decision.reason()).isEqualTo("stale_completion");
        }

        @Test
        @DisplayName("예상 완료 시각보다 지나치게 이른 완료 신호는 보류한다")
        void shouldDefer_WhenCompletionDetectedTooEarly() {
            // Given
            var nowKst = LocalDateTime.now(KOREA_ZONE);
            var status = buildWasherStatus("stop", "finish", isoUtc(nowKst));
            when(reservation.getStartTime()).thenReturn(nowKst.minusMinutes(1));
            when(reservation.getExpectedCompletionTime()).thenReturn(nowKst.plusMinutes(10));
            givenDetectedCompletion(nowKst);

            // When
            var decision = completionDecisionSupport.decide(reservation, status, true);

            // Then
            assertThat(decision.isDeferred()).isTrue();
            assertThat(decision.reason()).isEqualTo("too_early_completion");
        }

        @Test
        @DisplayName("예상 완료 시각이 없으면 조기 완료 판정을 적용하지 않는다")
        void shouldReturnCompleted_WhenExpectedCompletionTimeIsNull() {
            // Given
            var nowKst = LocalDateTime.now(KOREA_ZONE);
            var status = buildWasherStatus("stop", "finish", isoUtc(nowKst));
            when(reservation.getStartTime()).thenReturn(nowKst.minusMinutes(30));
            when(reservation.getExpectedCompletionTime()).thenReturn(null);
            givenDetectedCompletion(nowKst);

            // When
            var decision = completionDecisionSupport.decide(reservation, status, true);

            // Then
            assertThat(decision.isCompleted()).isTrue();
        }
    }

    @Nested
    @DisplayName("완료 예정 시각 근처 정지 경로")
    class StoppedNearCompletionPathTest {

        @Test
        @DisplayName("완료 예정 시각이 지난 정지 상태는 완료로 판정한다")
        void shouldReturnCompleted_WhenStoppedAndCompletionTimePassed() {
            // Given
            var nowKst = LocalDateTime.now(KOREA_ZONE);
            var completionTime = nowKst.minusMinutes(1);
            var status = buildWasherStatus("stop", "spin", isoUtc(completionTime));
            when(reservation.getStartTime()).thenReturn(nowKst.minusMinutes(40));
            when(reservation.getExpectedCompletionTime()).thenReturn(completionTime);
            givenNoDetectedCompletion();

            // When
            var decision = completionDecisionSupport.decide(reservation, status, true);

            // Then
            assertThat(decision.isCompleted()).isTrue();
            assertThat(decision.reason()).isEqualTo("stopped_near_completion");
        }

        @Test
        @DisplayName("건조기도 완료 예정 시각이 지난 정지 상태는 완료로 판정한다")
        void shouldReturnCompletedForDryer_WhenStoppedAndCompletionTimePassed() {
            // Given
            var nowKst = LocalDateTime.now(KOREA_ZONE);
            var completionTime = nowKst.minusMinutes(1);
            var status = buildDryerStatus("stop", "drying", isoUtc(completionTime));
            when(reservation.getStartTime()).thenReturn(nowKst.minusMinutes(40));
            when(reservation.getExpectedCompletionTime()).thenReturn(completionTime);
            givenNoDetectedCompletion();

            // When
            var decision = completionDecisionSupport.decide(reservation, status, false);

            // Then
            assertThat(decision.isCompleted()).isTrue();
            assertThat(decision.reason()).isEqualTo("stopped_near_completion");
        }

        @Test
        @DisplayName("사이클 도중 잠깐 과거로 보고된 완료 시각은 조기 완료 가드에 걸려 보류한다")
        void shouldDefer_WhenStoppedMidCycleWithPastCompletionTime() {
            // Given
            var nowKst = LocalDateTime.now(KOREA_ZONE);
            var reportedCompletionTime = nowKst.minusMinutes(1);
            var status = buildWasherStatus("stop", "wash", isoUtc(reportedCompletionTime));
            when(reservation.getStartTime()).thenReturn(nowKst.minusMinutes(10));
            when(reservation.getExpectedCompletionTime()).thenReturn(nowKst.plusMinutes(50));
            givenNoDetectedCompletion();

            // When
            var decision = completionDecisionSupport.decide(reservation, status, true);

            // Then
            assertThat(decision.isDeferred()).isTrue();
            assertThat(decision.reason()).isEqualTo("too_early_completion");
        }

        @Test
        @DisplayName("완료 예정 시각이 아직 오지 않았으면 완료 후보로 보지 않는다")
        void shouldReturnNotCompleted_WhenCompletionTimeStillInFuture() {
            // Given
            var nowKst = LocalDateTime.now(KOREA_ZONE);
            var status = buildWasherStatus("stop", "spin", isoUtc(nowKst.plusMinutes(3)));
            when(reservation.getStartTime()).thenReturn(nowKst.minusMinutes(40));
            givenNoDetectedCompletion();

            // When
            var decision = completionDecisionSupport.decide(reservation, status, true);

            // Then
            assertThat(decision.isCompleted()).isFalse();
            assertThat(decision.isDeferred()).isFalse();
        }

        @Test
        @DisplayName("완료 예정 시각이 유예 범위를 벗어난 정지는 완료 후보로 보지 않는다")
        void shouldReturnNotCompleted_WhenCompletionTimeOutsideGracePeriod() {
            // Given
            var nowKst = LocalDateTime.now(KOREA_ZONE);
            var status = buildWasherStatus("stop", "wash", isoUtc(nowKst.plusMinutes(30)));
            when(reservation.getStartTime()).thenReturn(nowKst.minusMinutes(10));
            givenNoDetectedCompletion();

            // When
            var decision = completionDecisionSupport.decide(reservation, status, true);

            // Then
            assertThat(decision.isCompleted()).isFalse();
            assertThat(decision.isDeferred()).isFalse();
        }
    }

    @Nested
    @DisplayName("완료 예정 시각 근처 정지 여부 판정")
    class IsStoppedNearCompletionTest {

        @Test
        @DisplayName("완료 예정 시각이 유예 범위 안이면 근처 정지로 판정한다")
        void shouldReturnTrue_WhenCompletionTimeWithinGracePeriod() {
            // Given
            var nowKst = LocalDateTime.now(KOREA_ZONE);
            var status = buildWasherStatus("stop", "spin", isoUtc(nowKst.plusMinutes(3)));
            when(reservation.getStartTime()).thenReturn(nowKst.minusMinutes(40));

            // When
            var result = completionDecisionSupport.isStoppedNearCompletion(reservation, status, true);

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("기기가 정지 상태가 아니면 근처 정지가 아니다")
        void shouldReturnFalse_WhenMachineNotStopped() {
            // Given
            var nowKst = LocalDateTime.now(KOREA_ZONE);
            var status = buildWasherStatus("run", "spin", isoUtc(nowKst.plusMinutes(3)));

            // When
            var result = completionDecisionSupport.isStoppedNearCompletion(reservation, status, true);

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("완료 예정 시각이 유예 범위를 벗어나면 근처 정지가 아니다")
        void shouldReturnFalse_WhenCompletionTimeOutsideGracePeriod() {
            // Given
            var nowKst = LocalDateTime.now(KOREA_ZONE);
            var status = buildWasherStatus("stop", "wash", isoUtc(nowKst.plusMinutes(30)));
            when(reservation.getStartTime()).thenReturn(nowKst.minusMinutes(10));

            // When
            var result = completionDecisionSupport.isStoppedNearCompletion(reservation, status, true);

            // Then
            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("jobState 리셋 정지 경로")
    class StoppedResetCompletionPathTest {

        @Test
        @DisplayName("정지 상태에서 jobState가 리셋되고 완료 예정 시각이 미래로 되돌아가면 현재 시각으로 완료 판정한다")
        void shouldReturnCompleted_WhenStoppedWithJobResetAndFutureCompletionTime() {
            // Given
            var nowKst = LocalDateTime.now(KOREA_ZONE);
            var startTime = nowKst.minusMinutes(60);
            var status = buildWasherStatus("stop", isoUtc(nowKst), "none", isoUtc(nowKst), isoUtc(nowKst.plusHours(1)));
            when(reservation.getStartTime()).thenReturn(startTime);
            when(reservation.getExpectedCompletionTime()).thenReturn(nowKst.minusMinutes(1));
            givenNoDetectedCompletion();

            // When
            var decision = completionDecisionSupport.decide(reservation, status, true);

            // Then
            assertThat(decision.isCompleted()).isTrue();
            assertThat(decision.reason()).isEqualTo("stopped_reset_completion");
            assertThat(decision.completionTime()).isBetween(nowKst, nowKst.plusMinutes(1));
        }

        @Test
        @DisplayName("전원이 꺼진 정지는 완료가 아니라 중단이므로 완료 후보로 보지 않는다")
        void shouldReturnNotCompleted_WhenPoweredOff() {
            // Given
            var nowKst = LocalDateTime.now(KOREA_ZONE);
            var status = buildWasherStatus("stop", isoUtc(nowKst), "none", isoUtc(nowKst), isoUtc(nowKst.plusHours(1)));
            when(reservation.getStartTime()).thenReturn(nowKst.minusMinutes(60));
            when(machineStateDetectionSupport.isPoweredOff(status)).thenReturn(true);
            givenNoDetectedCompletion();

            // When
            var decision = completionDecisionSupport.decide(reservation, status, true);

            // Then
            assertThat(decision.isCompleted()).isFalse();
            assertThat(decision.isDeferred()).isFalse();
        }

        @Test
        @DisplayName("상태 갱신 시각이 모두 예약 시작 전이면 이전 사이클의 잔재로 보고 완료 후보로 보지 않는다")
        void shouldReturnNotCompleted_WhenAllTimestampsBeforeStartTime() {
            // Given
            var nowKst = LocalDateTime.now(KOREA_ZONE);
            var startTime = nowKst.minusMinutes(10);
            var staleTimestamp = isoUtc(startTime.minusMinutes(5));
            var status = buildWasherStatus("stop", staleTimestamp, "none", staleTimestamp, isoUtc(nowKst.plusHours(1)));
            when(reservation.getStartTime()).thenReturn(startTime);
            givenNoDetectedCompletion();

            // When
            var decision = completionDecisionSupport.decide(reservation, status, true);

            // Then
            assertThat(decision.isCompleted()).isFalse();
            assertThat(decision.isDeferred()).isFalse();
        }

        @Test
        @DisplayName("jobState가 사이클 진행 중이면 완료 후보로 보지 않는다")
        void shouldReturnNotCompleted_WhenJobStateStillRunning() {
            // Given
            var nowKst = LocalDateTime.now(KOREA_ZONE);
            var status = buildWasherStatus("stop", isoUtc(nowKst), "spin", isoUtc(nowKst), isoUtc(nowKst.plusHours(1)));
            when(reservation.getStartTime()).thenReturn(nowKst.minusMinutes(10));
            givenNoDetectedCompletion();

            // When
            var decision = completionDecisionSupport.decide(reservation, status, true);

            // Then
            assertThat(decision.isCompleted()).isFalse();
            assertThat(decision.isDeferred()).isFalse();
        }

        @Test
        @DisplayName("완료 예정 시각이 이미 지난 경우는 이 경로가 아니라 근처 정지 경로가 처리한다")
        void shouldReturnNotCompleted_WhenCompletionTimeAlreadyPassedOutsideGrace() {
            // Given
            var nowKst = LocalDateTime.now(KOREA_ZONE);
            var status = buildWasherStatus("stop",
                    isoUtc(nowKst),
                    "none",
                    isoUtc(nowKst),
                    isoUtc(nowKst.minusMinutes(30)));
            when(reservation.getStartTime()).thenReturn(nowKst.minusMinutes(60));
            givenNoDetectedCompletion();

            // When
            var decision = completionDecisionSupport.decide(reservation, status, true);

            // Then
            assertThat(decision.isCompleted()).isFalse();
            assertThat(decision.isDeferred()).isFalse();
        }
    }
}
