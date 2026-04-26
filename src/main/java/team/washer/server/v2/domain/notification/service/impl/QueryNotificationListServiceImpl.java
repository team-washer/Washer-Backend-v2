package team.washer.server.v2.domain.notification.service.impl;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import team.themoment.sdk.exception.ExpectedException;
import team.washer.server.v2.domain.notification.dto.response.NotificationListResDto;
import team.washer.server.v2.domain.notification.dto.response.NotificationResDto;
import team.washer.server.v2.domain.notification.entity.Notification;
import team.washer.server.v2.domain.notification.repository.NotificationRepository;
import team.washer.server.v2.domain.notification.service.QueryNotificationListService;
import team.washer.server.v2.domain.user.entity.User;
import team.washer.server.v2.domain.user.repository.UserRepository;
import team.washer.server.v2.global.security.provider.CurrentUserProvider;

@Service
@RequiredArgsConstructor
public class QueryNotificationListServiceImpl implements QueryNotificationListService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final CurrentUserProvider currentUserProvider;

    @Override
    @Transactional(readOnly = true)
    public NotificationListResDto execute() {
        final var userId = currentUserProvider.getCurrentUserId();
        final User user = userRepository.findById(userId)
                .orElseThrow(() -> new ExpectedException("사용자를 찾을 수 없습니다", HttpStatus.NOT_FOUND));

        final List<Notification> notifications = notificationRepository.findByUserOrderByCreatedAtDesc(user);

        final List<NotificationResDto> notificationResDtos = notifications.stream().map(this::toResDto).toList();

        return new NotificationListResDto(notificationResDtos);
    }

    private NotificationResDto toResDto(final Notification notification) {
        return new NotificationResDto(notification.getId(),
                notification.getType(),
                notification.getMessage(),
                notification.getCreatedAt());
    }
}
