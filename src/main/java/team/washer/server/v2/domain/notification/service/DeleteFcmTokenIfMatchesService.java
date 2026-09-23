package team.washer.server.v2.domain.notification.service;

public interface DeleteFcmTokenIfMatchesService {

    void execute(Long userId, String token);
}
