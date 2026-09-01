package team.washer.server.v2.domain.reservation.util;

import java.util.List;
import java.util.Optional;

import team.washer.server.v2.domain.reservation.entity.Reservation;

/**
 * 하나의 기기에 걸린 활성 예약 중 대표 예약 하나를 고르는 규칙.
 *
 * <p>
 * RUNNING을 RESERVED보다 우선한다. 상태 드리프트로 두 상태가 공존할 때 나중에 생성된 RESERVED가 실제로 돌고 있는
 * RUNNING을 가리면, 실제로는 사용 중인 기기가 예약 가능한 것처럼 표시되기 때문이다. 같은 상태 안에서는 넘겨받은 순서(조회 시점의
 * 최근 생성 순)를 그대로 유지한다.
 *
 * <p>
 * 목록 API와 강제 정지가 서로 다른 규칙을 쓰던 것을 이 한 곳으로 모았다.
 */
public final class ActiveReservationSelector {

    private ActiveReservationSelector() {
    }

    /**
     * 활성 예약 목록에서 대표 예약을 고른다.
     *
     * @param activeReservations
     *            RESERVED · RUNNING 상태의 예약 목록. 최근 생성 순으로 정렬되어 있다고 가정한다
     * @return 대표 예약. 활성 예약이 없으면 {@link Optional#empty()}
     */
    public static Optional<Reservation> selectPrimary(List<Reservation> activeReservations) {
        if (activeReservations == null || activeReservations.isEmpty()) {
            return Optional.empty();
        }
        return activeReservations.stream().filter(Reservation::isRunning).findFirst().or(() -> activeReservations
                .stream().filter(reservation -> reservation.isReserved() && !reservation.isExpired()).findFirst());
    }
}
