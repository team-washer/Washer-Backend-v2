package team.washer.server.v2.domain.notification.service;

import team.washer.server.v2.domain.user.entity.User;

public interface SendFcmNotificationService {

    void execute(User user, String title, String body);
}
