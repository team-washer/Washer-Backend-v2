package team.washer.server.v2.domain.notification.support;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
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

import lombok.extern.slf4j.Slf4j;
import team.washer.server.v2.domain.notification.service.DeleteFcmTokenIfMatchesService;
import team.washer.server.v2.domain.user.entity.User;
import team.washer.server.v2.global.config.AsyncConfig;

/**
 * FCM 푸시 알림 전송을 담당하는 지원 컴포넌트.
 */
@Slf4j
@Component
public class FcmNotificationSupport {

    private final FirebaseMessaging firebaseMessaging;
    private final DeleteFcmTokenIfMatchesService deleteFcmTokenIfMatchesService;
    private final TaskExecutor fcmTaskExecutor;

    public FcmNotificationSupport(final FirebaseMessaging firebaseMessaging,
            final DeleteFcmTokenIfMatchesService deleteFcmTokenIfMatchesService,
            @Qualifier(AsyncConfig.FCM_TASK_EXECUTOR) final TaskExecutor fcmTaskExecutor) {
        this.firebaseMessaging = firebaseMessaging;
        this.deleteFcmTokenIfMatchesService = deleteFcmTokenIfMatchesService;
        this.fcmTaskExecutor = fcmTaskExecutor;
    }

    /**
     * FCM 푸시 알림 전송을 전용 실행기에 넘긴다. 커밋 후 전송 예약은 호출자가 담당한다.
     *
     * <p>
     * 호출 스레드에서는 사용자 식별자와 토큰만 읽고 Firebase 호출은 전용 스레드에서 수행하므로, 호출자가 점유한 DB 연결이 전송
     * 시간만큼 묶이지 않는다. 대기열이 가득 찼거나 실행기가 종료 중이면 전송을 버리고 경고 로그만 남긴다. 무효 토큰 오류가 발생하면 저장된
     * 토큰을 조건부 삭제하되, 다른 스레드가 소유한 {@code user} 엔티티는 변경하지 않는다.
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
            fcmTaskExecutor.execute(() -> sendQuietly(userId, token, title, body));
        } catch (TaskRejectedException e) {
            log.warn("FCM notification dropped executor rejected task userId={}", userId);
        }
    }

    private void sendQuietly(final Long userId, final String token, final String title, final String body) {
        try {
            dispatch(userId, token, title, body);
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
        try {
            return dispatch(user.getId(), user.getFcmToken(), title, body);
        } catch (FirebaseMessagingException e) {
            if (isInvalidToken(e.getMessagingErrorCode())) {
                user.clearFcmToken();
            }
            throw e;
        }
    }

    private String dispatch(final Long userId, final String token, final String title, final String body)
            throws FirebaseMessagingException {
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
            deleteTokenIfInvalid(userId, token, errorCode);
            throw e;
        }
    }

    private boolean isInvalidToken(final MessagingErrorCode errorCode) {
        return errorCode == MessagingErrorCode.UNREGISTERED || errorCode == MessagingErrorCode.INVALID_ARGUMENT;
    }

    private void deleteTokenIfInvalid(final Long userId, final String token, final MessagingErrorCode errorCode) {
        if (!isInvalidToken(errorCode)) {
            return;
        }

        log.warn("Removing invalid FCM token userId={} errorCode={}", userId, errorCode);
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
