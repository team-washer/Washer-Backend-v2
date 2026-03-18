package team.washer.server.v2.domain.notification.service;

public interface RegisterFcmTokenService {

    void execute(Long userId, String token);
}
