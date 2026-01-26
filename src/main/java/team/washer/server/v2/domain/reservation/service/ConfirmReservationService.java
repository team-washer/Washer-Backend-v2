package team.washer.server.v2.domain.reservation.service;

public interface ConfirmReservationService {
    void execute(Long reservationId, Long userId);
}
