package team.washer.server.v2.domain.notification.service;

import team.washer.server.v2.domain.machine.entity.Machine;
import team.washer.server.v2.domain.user.entity.User;

public interface SendCompletionNotificationService {
    void execute(User user, Machine machine);
}
