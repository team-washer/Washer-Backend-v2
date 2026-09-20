package team.washer.server.v2.domain.notification.support;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

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
import team.washer.server.v2.domain.notification.service.DeleteFcmTokenIfMatchesService;
import team.washer.server.v2.domain.user.entity.User;

/**
 * FCM 푸시 알림 전송을 담당하는 지원 컴포넌트.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FcmNotificationSupport {

    private final FirebaseMessaging firebaseMessaging;
    private final DeleteFcmTokenIfMatchesService deleteFcmTokenIfMatchesService;

    /**
     * FCM 푸시 알림을 전송한다.
     */
    public void send(final User user, final String title, final String body) {
        final Long userId = user.getId();
        final String token = user.getFcmToken();
        if (token == null || token.isBlank()) {
            log.info("FCM token not found skipping notification userId={}", userId);
            return;
        }

        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {

                @Override
                public void afterCommit() {
                    sendNow(userId, token, title, body);
                }
            });
            return;
        }

        sendNow(userId, token, title, body);
    }

    private void sendNow(final Long userId, final String token, final String title, final String body) {
        if (token == null || token.isBlank()) {
            log.info("FCM token not found skipping notification userId={}", userId);
            return;
        }

        try {
            final var notification = Notification.builder().setTitle(title).setBody(body).build();
            final var messageBuilder = Message.builder().setToken(token).setNotification(notification)
                    .setAndroidConfig(androidConfig(title, body)).setApnsConfig(apnsConfig(title, body))
                    .setWebpushConfig(webpushConfig(title, body));
            if (title != null) {
                messageBuilder.putData("title", title);
            }
            if (body != null) {
                messageBuilder.putData("body", body);
            }
            final var message = messageBuilder.build();
            final String messageId = firebaseMessaging.send(message);
            log.info("FCM notification sent successfully userId={} messageId={}", userId, messageId);
        } catch (FirebaseMessagingException e) {
            final MessagingErrorCode errorCode = e.getMessagingErrorCode();
            log.error("Failed to send FCM notification userId={} errorCode={}", userId, errorCode, e);
            if (errorCode == MessagingErrorCode.UNREGISTERED || errorCode == MessagingErrorCode.INVALID_ARGUMENT) {
                log.warn("Removing invalid FCM token userId={} errorCode={}", userId, errorCode);
                try {
                    deleteFcmTokenIfMatchesService.execute(userId, token);
                } catch (RuntimeException cleanupException) {
                    log.error("Failed to remove invalid FCM token userId={}", userId, cleanupException);
                }
            }
        } catch (RuntimeException e) {
            log.error("Failed to prepare or send FCM notification userId={}", userId, e);
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
