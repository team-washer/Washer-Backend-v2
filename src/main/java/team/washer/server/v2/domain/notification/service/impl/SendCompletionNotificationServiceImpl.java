package team.washer.server.v2.domain.notification.service.impl;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import team.washer.server.v2.domain.machine.entity.Machine;
import team.washer.server.v2.domain.notification.entity.Notification;
import team.washer.server.v2.domain.notification.repository.NotificationRepository;
import team.washer.server.v2.domain.notification.service.SendCompletionNotificationService;
import team.washer.server.v2.domain.user.entity.User;

@Service
@RequiredArgsConstructor
@Slf4j
public class SendCompletionNotificationServiceImpl implements SendCompletionNotificationService {

    private final NotificationRepository notificationRepository;

    @Override
    @Transactional
    public void execute(User user, Machine machine) {
        try {
            var notification = Notification.createCompletionNotification(user, machine);
            notificationRepository.save(notification);
            log.info("Completion notification sent to user {} for machine {}", user.getId(), machine.getName());
        } catch (Exception e) {
            log.error("Failed to send completion notification to user {} for machine {}",
                    user.getId(),
                    machine.getName(),
                    e);
        }
    }
}
