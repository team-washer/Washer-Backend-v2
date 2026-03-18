package team.washer.server.v2.domain.notification.service.impl;

import org.springframework.stereotype.Service;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.Notification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import team.washer.server.v2.domain.notification.service.DeleteFcmTokenService;
import team.washer.server.v2.domain.notification.service.SendFcmNotificationService;
import team.washer.server.v2.domain.user.entity.User;

@Slf4j
@Service
@RequiredArgsConstructor
public class SendFcmNotificationServiceImpl implements SendFcmNotificationService {

    private final FirebaseMessaging firebaseMessaging;
    private final DeleteFcmTokenService deleteFcmTokenService;

    @Override
    public void execute(final User user, final String title, final String body) {
        final String token = user.getFcmToken();
        if (token == null || token.isBlank()) {
            log.debug("FCM token not found, skipping notification: userId={}", user.getId());
            return;
        }

        try {
            final var notification = Notification.builder().setTitle(title).setBody(body).build();
            final var message = Message.builder().setToken(token).setNotification(notification).build();
            final String messageId = firebaseMessaging.send(message);
            log.info("FCM notification sent successfully: userId={}, messageId={}", user.getId(), messageId);
        } catch (FirebaseMessagingException e) {
            final MessagingErrorCode errorCode = e.getMessagingErrorCode();
            log.error("Failed to send FCM notification: userId={}, errorCode={}", user.getId(), errorCode, e);
            if (errorCode == MessagingErrorCode.UNREGISTERED || errorCode == MessagingErrorCode.INVALID_ARGUMENT) {
                log.warn("Removing invalid FCM token: userId={}, errorCode={}", user.getId(), errorCode);
                deleteFcmTokenService.execute(user.getId());
            }
        }
    }
}
