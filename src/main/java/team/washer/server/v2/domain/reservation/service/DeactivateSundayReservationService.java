package team.washer.server.v2.domain.reservation.service;

public interface DeactivateSundayReservationService {
    void execute(Long adminId, String notes);
}
