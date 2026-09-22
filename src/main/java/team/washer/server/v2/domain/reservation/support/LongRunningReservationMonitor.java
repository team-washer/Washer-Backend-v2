package team.washer.server.v2.domain.reservation.support;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;
import team.washer.server.v2.domain.reservation.service.impl.ReservationLifecycleProcessor.LongRunningReservation;
import team.washer.server.v2.global.common.constants.ReservationConstants;

/**
 * RUNNING 예약의 SmartThings 조회 실패와 장기 실행을 운영 로그로 식별하는 컴포넌트.
 *
 * <p>
 * 예약별 연속 조회 실패 횟수를 추적하고, 장기 실행 기준을 넘긴 예약을 조회 실패 횟수와 함께 경고 로그로 남긴다. 같은 예약은
 * {@link ReservationConstants#LONG_RUNNING_REPORT_INTERVAL_MINUTES}분 간격으로만 다시
 * 보고한다.
 *
 * <p>
 * 식별만 담당하며 예약을 완료·취소하거나 기기를 해제하지 않는다. 추적 값은 메모리에만 두므로 서버가 재시작되면 초기화되고, 장기 실행
 * 예약은 재시작 후 첫 주기에 다시 보고된다.
 */
@Component
@Slf4j
public class LongRunningReservationMonitor {

    private final Map<Long, Integer> consecutiveQueryFailures = new ConcurrentHashMap<>();
    private final Map<Long, LocalDateTime> lastReportedAt = new ConcurrentHashMap<>();

    /**
     * 기기 상태 조회에 성공한 예약의 연속 조회 실패 횟수를 초기화한다.
     */
    public void recordQuerySuccess(Long reservationId) {
        consecutiveQueryFailures.remove(reservationId);
    }

    /**
     * 기기 상태 조회에 실패한 예약의 연속 조회 실패 횟수를 증가시킨다.
     *
     * @return 증가된 연속 조회 실패 횟수
     */
    public int recordQueryFailure(Long reservationId) {
        return consecutiveQueryFailures.merge(reservationId, 1, Integer::sum);
    }

    /**
     * 더 이상 RUNNING이 아닌 예약의 추적 값을 제거한다.
     *
     * @param runningReservationIds
     *            현재 RUNNING인 예약 ID 목록
     */
    public void retainOnly(Collection<Long> runningReservationIds) {
        consecutiveQueryFailures.keySet().retainAll(runningReservationIds);
        lastReportedAt.keySet().retainAll(runningReservationIds);
    }

    /**
     * 장기 실행 예약을 경고 로그로 보고한다. 마지막 보고 후 보고 간격이 지나지 않은 예약은 건너뛴다.
     *
     * @param reservations
     *            장기 실행 기준을 넘긴 RUNNING 예약 목록
     * @param now
     *            판정 기준 현재 시각
     */
    public void report(List<LongRunningReservation> reservations, LocalDateTime now) {
        for (var reservation : reservations) {
            var reportedAt = lastReportedAt.get(reservation.reservationId());
            if (reportedAt != null && Duration.between(reportedAt, now)
                    .toMinutes() < ReservationConstants.LONG_RUNNING_REPORT_INTERVAL_MINUTES) {
                continue;
            }
            lastReportedAt.put(reservation.reservationId(), now);
            log.warn(
                    "long running reservation detected reservationId={} machineName={} deviceId={} startTime={} "
                            + "expectedCompletionTime={} runningMinutes={} consecutiveQueryFailures={}",
                    reservation.reservationId(),
                    reservation.machineName(),
                    reservation.deviceId(),
                    reservation.startTime(),
                    reservation.expectedCompletionTime(),
                    Duration.between(reservation.startTime(), now).toMinutes(),
                    consecutiveQueryFailures.getOrDefault(reservation.reservationId(), 0));
        }
    }
}
