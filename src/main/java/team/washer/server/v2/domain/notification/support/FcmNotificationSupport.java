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
     * FCM 푸시 알림을 즉시 전송한다. 커밋 후 전송 예약은 호출자가 담당한다.
     *
     * <p>
     * 무효 토큰 오류가 발생하면 전달받은 {@code user}의 FCM 토큰도 비운다.
     * </p>
     */
    public void send(final User user, final String title, final String body) {
        final Long userId = user.getId();
        final String token = user.getFcmToken();
        if (token == null || token.isBlank()) {
            log.info("FCM token not found skipping notification userId={}", userId);
            return;
        }

        try {
            dispatch(user, userId, token, title, body);
        } catch (FirebaseMessagingException e) {
            // dispatch에서 이미 로그와 토큰 정리를 수행했으므로 예약 알림 흐름에서는 삼킨다.
        } catch (RuntimeException e) {
            log.error("Failed to prepare or send FCM notification userId={}", userId, e);
        }
    }

    /**
     * FCM 푸시 알림을 즉시 전송하고 Firebase가 발급한 메시지 ID를 반환한다.
     *
     * <p>
     * {@link #send(User, String, String)}와 달리 전송 실패를 호출자에게 전파한다. 발송 결과를 확인해야 하는 관리자
     * 테스트 발송에서 사용한다. 무효 토큰({@code UNREGISTERED}, {@code INVALID_ARGUMENT})은 예외를 던지기
     * 전에 정리하고 전달받은 {@code user}의 FCM 토큰도 비운다.
     * </p>
     *
     * @param user
     *            발송 대상 사용자
     * @param title
     *            알림 제목
     * @param body
     *            알림 본문
     * @return Firebase가 발급한 메시지 ID
     * @throws FirebaseMessagingException
     *             FCM 발송에 실패한 경우
     */
    public String sendAndGetMessageId(final User user, final String title, final String body)
            throws FirebaseMessagingException {
        return dispatch(user, user.getId(), user.getFcmToken(), title, body);
    }

    private String dispatch(final User user,
            final Long userId,
            final String token,
            final String title,
            final String body) throws FirebaseMessagingException {
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
            return messageId;
        } catch (FirebaseMessagingException e) {
            final MessagingErrorCode errorCode = e.getMessagingErrorCode();
            log.error("Failed to send FCM notification userId={} errorCode={}", userId, errorCode, e);
            deleteTokenIfInvalid(user, userId, token, errorCode);
            throw e;
        }
    }

    private void deleteTokenIfInvalid(final User user,
            final Long userId,
            final String token,
            final MessagingErrorCode errorCode) {
        if (errorCode != MessagingErrorCode.UNREGISTERED && errorCode != MessagingErrorCode.INVALID_ARGUMENT) {
            return;
        }

        log.warn("Removing invalid FCM token userId={} errorCode={}", userId, errorCode);
        user.clearFcmToken();
        try {
            deleteFcmTokenIfMatchesService.execute(userId, token);
        } catch (RuntimeException cleanupException) {
            log.error("Failed to remove invalid FCM token userId={}", userId, cleanupException);
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
