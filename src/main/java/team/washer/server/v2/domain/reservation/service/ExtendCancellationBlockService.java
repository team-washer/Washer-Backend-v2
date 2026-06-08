package team.washer.server.v2.domain.reservation.service;

public interface ExtendCancellationBlockService {
    void execute(Long userId, int days);
}
