package team.washer.server.v2.domain.notification.support;

import org.springframework.stereotype.Component;

import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.AndroidNotification;
import com.google.firebase.messaging.ApnsConfig;
import com.google.firebase.messaging.Aps;
import com.google.firebase.messaging.ApsAlert;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.Notification;
import com.google.firebase.messaging.WebpushConfig;
import com.google.firebase.messaging.WebpushNotification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import team.washer.server.v2.domain.notification.service.DeleteFcmTokenService;
import team.washer.server.v2.domain.user.entity.User;

/**
 * FCM 푸시 알림 전송을 담당하는 지원 컴포넌트.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FcmNotificationSupport {

    private final FirebaseMessaging firebaseMessaging;
    private final DeleteFcmTokenService deleteFcmTokenService;

    /**
     * FCM 푸시 알림을 전송한다.
     */
    public void send(final User user, final String title, final String body) {
        final String token = user.getFcmToken();
        if (token == null || token.isBlank()) {
            log.debug("FCM token not found skipping notification userId={}", user.getId());
            return;
        }

        try {
            final var notification = Notification.builder().setTitle(title).setBody(body).build();
            final var message = Message.builder().setToken(token).setNotification(notification).putData("title", title)
                    .putData("body", body).setAndroidConfig(androidConfig(title, body))
                    .setApnsConfig(apnsConfig(title, body)).setWebpushConfig(webpushConfig(title, body)).build();
            final String messageId = firebaseMessaging.send(message);
            log.info("FCM notification sent successfully userId={} messageId={}", user.getId(), messageId);
        } catch (FirebaseMessagingException e) {
            final MessagingErrorCode errorCode = e.getMessagingErrorCode();
            log.error("Failed to send FCM notification userId={} errorCode={}", user.getId(), errorCode, e);
            if (errorCode == MessagingErrorCode.UNREGISTERED || errorCode == MessagingErrorCode.INVALID_ARGUMENT) {
                log.warn("Removing invalid FCM token userId={} errorCode={}", user.getId(), errorCode);
                deleteFcmTokenService.execute(user.getId());
            }
        }
    }

    private AndroidConfig androidConfig(final String title, final String body) {
        return AndroidConfig.builder().setPriority(AndroidConfig.Priority.HIGH)
                .setNotification(AndroidNotification.builder().setTitle(title).setBody(body).build()).build();
    }

    private ApnsConfig apnsConfig(final String title, final String body) {
        final var alert = ApsAlert.builder().setTitle(title).setBody(body).build();
        return ApnsConfig.builder().setAps(Aps.builder().setAlert(alert).setSound("default").build()).build();
    }

    private WebpushConfig webpushConfig(final String title, final String body) {
        return WebpushConfig.builder()
                .setNotification(WebpushNotification.builder().setTitle(title).setBody(body).build()).build();
    }
}
