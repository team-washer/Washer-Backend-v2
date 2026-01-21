package team.washer.server.v2.domain.reservation.service;

public interface ClearUserPenaltyService {
    void execute(Long adminId, Long userId);
}
