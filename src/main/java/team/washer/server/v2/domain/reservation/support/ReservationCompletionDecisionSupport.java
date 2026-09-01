package team.washer.server.v2.domain.reservation.support;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import team.washer.server.v2.domain.reservation.entity.Reservation;
import team.washer.server.v2.domain.smartthings.dto.response.SmartThingsDeviceStatusResDto;
import team.washer.server.v2.domain.smartthings.support.MachineCompletionSignal;
import team.washer.server.v2.domain.smartthings.support.MachineStateDetectionSupport;
import team.washer.server.v2.global.util.DateTimeUtil;

/**
 * 기기 사이클의 완료 여부를 판정하는 단일 진입점.
 *
 * <p>
 * 완료 후보를 찾는 경로는 세 가지다. SmartThings가 jobState·machineState로 완료를 보고한 경우
 * ({@code smartthings_completed}), 완료 예정 시각 근처에서 기기가 정지한 채 그 시각이 지난 경우
 * ({@code stopped_near_completion}), 그리고 기기가 정지하면서 jobState를 리셋하고 완료 예정 시각을 다음
 * 사이클 기준의 미래 값으로 되돌린 경우({@code stopped_reset_completion})다. 세 경로 모두 동일한 가드(이전
 * 사이클 신호 검사·조기 완료 검사)를 거치므로, 어느 경로로 들어오든 같은 질문에 같은 답이 나온다.
 *
 * <p>
 * 이 컴포넌트는 판정만 하고 상태를 바꾸지 않는다. 디바운스와 DB 갱신은 호출 측
 * ({@code ReservationLifecycleProcessor})의 책임이다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ReservationCompletionDecisionSupport {

    private static final String REASON_SMARTTHINGS_COMPLETED = "smartthings_completed";
    private static final String REASON_STOPPED_NEAR_COMPLETION = "stopped_near_completion";
    private static final String REASON_STOPPED_RESET_COMPLETION = "stopped_reset_completion";
    private static final String REASON_STALE_COMPLETION = "stale_completion";
    private static final String REASON_TOO_EARLY_COMPLETION = "too_early_completion";

    private static final long COMPLETION_EARLY_TOLERANCE_MINUTES = 2;
    private static final long COMPLETION_BOUNDARY_GRACE_MINUTES = 5;

    private final MachineStateDetectionSupport machineStateDetectionSupport;

    /**
     * 예약과 기기 상태를 바탕으로 사이클 완료 여부를 판정한다.
     *
     * @param reservation
     *            판정 대상 RUNNING 예약
     * @param status
     *            SmartThings에서 조회한 기기 상태
     * @param isWasher
     *            세탁기 여부
     * @return 완료 판정 결과
     */
    public CompletionDecision decide(Reservation reservation, SmartThingsDeviceStatusResDto status, boolean isWasher) {
        var candidate = findCompletionCandidate(reservation, status, isWasher);
        if (candidate.isEmpty()) {
            return CompletionDecision.notCompleted();
        }

        var completionTime = candidate.get().completionTime();
        if (isStaleCompletion(reservation, status, isWasher, completionTime)) {
            return CompletionDecision.deferred(completionTime, REASON_STALE_COMPLETION);
        }
        if (isTooEarlyCompletion(reservation, completionTime)) {
            return CompletionDecision.deferred(completionTime, REASON_TOO_EARLY_COMPLETION);
        }
        return CompletionDecision.completed(completionTime, candidate.get().reason());
    }

    /**
     * 완료 예정 시각 근처에서 기기가 정지한 상태인지 판정한다. 사이클 종료 직전의 정지를 비정상 중단으로 오판하지 않기 위해 사용한다.
     *
     * @param reservation
     *            판정 대상 RUNNING 예약
     * @param status
     *            SmartThings에서 조회한 기기 상태
     * @param isWasher
     *            세탁기 여부
     * @return 완료 예정 시각 근처의 정지이면 {@code true}
     */
    public boolean isStoppedNearCompletion(Reservation reservation,
            SmartThingsDeviceStatusResDto status,
            boolean isWasher) {
        return findNearCompletionStopTime(reservation, status, isWasher).isPresent();
    }

    private Optional<CompletionCandidate> findCompletionCandidate(Reservation reservation,
            SmartThingsDeviceStatusResDto status,
            boolean isWasher) {
        var signal = machineStateDetectionSupport.detectCompletion(status, isWasher);
        if (signal.isCompleted()) {
            return Optional.of(new CompletionCandidate(signal.completionTime(), REASON_SMARTTHINGS_COMPLETED));
        }

        var nearCompletion = findNearCompletionStopTime(reservation, status, isWasher)
                .filter(completionTime -> !completionTime.isAfter(DateTimeUtil.nowInKorea()))
                .map(completionTime -> new CompletionCandidate(completionTime, REASON_STOPPED_NEAR_COMPLETION));
        if (nearCompletion.isPresent()) {
            return nearCompletion;
        }

        return findStoppedResetCompletionTime(reservation, status, isWasher, signal)
                .map(completionTime -> new CompletionCandidate(completionTime, REASON_STOPPED_RESET_COMPLETION));
    }

    /**
     * 기기가 이번 사이클을 끝내고 정지하면서 jobState를 리셋하고, 완료 예정 시각을 다음 사이클 기준의 미래 값으로 되돌린 상태인지
     * 판정한다.
     *
     * <p>
     * 이 상태의 감지 자체는 {@link MachineStateDetectionSupport#detectCompletion}가
     * {@code JOB_RESET_WITH_FUTURE_COMPLETION} 신호로 알려준다. 보고된 완료 예정 시각은 이번 사이클의 종료
     * 시각이 아니므로 완료 시각으로 쓸 수 없고, 그대로 두면 사이클이 끝났는데도 예약이 RUNNING 으로 남아 기기가 계속 IN_USE로
     * 표시된다. 여기서는 이번 사이클에서 실제로 상태가 갱신됐는지를 예약 시작 시각과 대조해 확인한 뒤 완료 시각을 현재 시각으로 본다.
     *
     * <p>
     * 전원이 꺼진 정지는 완료가 아니라 중단이므로 여기서 제외한다.
     */
    private Optional<LocalDateTime> findStoppedResetCompletionTime(Reservation reservation,
            SmartThingsDeviceStatusResDto status,
            boolean isWasher,
            MachineCompletionSignal signal) {
        if (!signal.isJobResetWithFutureCompletion() || machineStateDetectionSupport.isPoweredOff(status)) {
            return Optional.empty();
        }

        var startTime = reservation.getStartTime();
        if (startTime == null || signal.completionTime().isBefore(startTime)) {
            return Optional.empty();
        }
        // 이번 사이클에서 실제로 상태가 갱신됐다는 근거가 없으면 이전 사이클의 잔재로 본다.
        if (!hasStateTimestampAtOrAfter(status, isWasher, startTime)) {
            return Optional.empty();
        }

        log.debug("device stopped with job reset and future completion time completionTime={} startTime={}",
                signal.completionTime(),
                startTime);
        return Optional.of(DateTimeUtil.nowInKorea());
    }

    /**
     * 기기가 정지한 상태이고 보고된 완료 예정 시각이 현재 시각과 유예 범위 안에 있으면 그 시각을 반환한다.
     */
    private Optional<LocalDateTime> findNearCompletionStopTime(Reservation reservation,
            SmartThingsDeviceStatusResDto status,
            boolean isWasher) {
        if (!machineStateDetectionSupport.isStopped(status, isWasher)) {
            return Optional.empty();
        }

        var completionTime = machineStateDetectionSupport.resolveCompletionTime(status, isWasher).orElse(null);
        if (completionTime == null) {
            return Optional.empty();
        }

        var startTime = reservation.getStartTime();
        if (startTime != null && completionTime.isBefore(startTime)) {
            return Optional.empty();
        }

        var secondsFromNow = Math.abs(Duration.between(DateTimeUtil.nowInKorea(), completionTime).toSeconds());
        if (secondsFromNow > Duration.ofMinutes(COMPLETION_BOUNDARY_GRACE_MINUTES).toSeconds()) {
            return Optional.empty();
        }

        return Optional.of(completionTime);
    }

    /**
     * 완료 신호가 이전 사이클에서 남은 값인지 판정한다. 완료 시각이나 상태 갱신 시각이 이번 예약의 시작 시각보다 앞서면 이전 사이클의 잔재로
     * 본다.
     */
    private boolean isStaleCompletion(Reservation reservation,
            SmartThingsDeviceStatusResDto status,
            boolean isWasher,
            LocalDateTime completionTime) {
        var startTime = reservation.getStartTime();
        if (startTime == null) {
            return false;
        }
        if (completionTime.isBefore(startTime)) {
            return true;
        }
        return stateTimestamps(status, isWasher).anyMatch(updatedAt -> updatedAt.isBefore(startTime));
    }

    /**
     * 예상 완료 시각보다 지나치게 이른 완료 신호인지 판정한다. 예상 완료 시각은 저장 시점에 상한 검증을 거치므로
     * ({@code Reservation.updateExpectedCompletionTime}) 여기서 별도의 이상치 예외 처리는 하지 않는다.
     */
    private boolean isTooEarlyCompletion(Reservation reservation, LocalDateTime completionTime) {
        var expectedCompletionTime = reservation.getExpectedCompletionTime();
        if (expectedCompletionTime == null) {
            return false;
        }
        return completionTime.isBefore(expectedCompletionTime.minusMinutes(COMPLETION_EARLY_TOLERANCE_MINUTES));
    }

    /**
     * 이번 사이클에서 상태가 갱신됐다는 근거가 하나라도 있는지 판정한다.
     */
    private boolean hasStateTimestampAtOrAfter(SmartThingsDeviceStatusResDto status,
            boolean isWasher,
            LocalDateTime startTime) {
        return stateTimestamps(status, isWasher).anyMatch(updatedAt -> !updatedAt.isBefore(startTime));
    }

    /**
     * 기기가 보고한 machineState·jobState 갱신 시각 중 해석 가능한 값만 흘려보낸다.
     */
    private Stream<LocalDateTime> stateTimestamps(SmartThingsDeviceStatusResDto status, boolean isWasher) {
        if (status == null) {
            return Stream.empty();
        }
        return Stream.of(status.getOperatingStateTimestamp(isWasher), status.getJobStateTimestamp(isWasher))
                .filter(timestamp -> timestamp != null && !timestamp.isBlank())
                .map(DateTimeUtil::parseAndConvertToKoreaTime).filter(Objects::nonNull);
    }

    private record CompletionCandidate(LocalDateTime completionTime, String reason) {
    }
}
