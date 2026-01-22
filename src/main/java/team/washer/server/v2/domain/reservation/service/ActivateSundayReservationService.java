package team.washer.server.v2.domain.reservation.service;

public interface ActivateSundayReservationService {
    void execute(Long adminId, String notes);
}
