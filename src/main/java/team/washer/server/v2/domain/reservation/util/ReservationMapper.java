package team.washer.server.v2.domain.reservation.util;

import team.washer.server.v2.domain.reservation.dto.response.ReservationResDto;
import team.washer.server.v2.domain.reservation.entity.Reservation;

/**
 * Reservation 엔티티와 DTO 간 변환을 처리하는 유틸리티 클래스
 *
 * <p>
 * 정적 팩토리 메서드를 통해 Reservation 엔티티를 ReservationResDto로 변환한다. 이 클래스는 인스턴스화될 수 없으며,
 * 모든 메서드는 정적 메서드로 제공된다.
 * </p>
 */
public final class ReservationMapper {

    private ReservationMapper() {
        throw new AssertionError("유틸리티 클래스는 인스턴스화할 수 없습니다");
    }

    /**
     * Reservation 엔티티를 ReservationResDto로 변환
     *
     * @param reservation
     *            변환할 예약 엔티티
     * @return 변환된 예약 응답 DTO
     */
    public static ReservationResDto toResDto(final Reservation reservation) {
        return new ReservationResDto(reservation.getId(),
                reservation.getUser().getId(),
                reservation.getUser().getName(),
                reservation.getUser().getRoomNumber(),
                reservation.getMachine().getId(),
                reservation.getMachine().getName(),
                reservation.getReservedAt(),
                reservation.getStartTime(),
                reservation.getExpectedCompletionTime(),
                reservation.getActualCompletionTime(),
                reservation.getStatus(),
                reservation.getConfirmedAt(),
                reservation.getCancelledAt(),
                reservation.getDayOfWeek(),
                reservation.getCreatedAt(),
                reservation.getUpdatedAt());
    }
}
