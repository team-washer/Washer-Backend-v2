package team.washer.server.v2.domain.notification.service;

import static org.mockito.BDDMockito.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;

import team.washer.server.v2.domain.notification.service.impl.SendFcmNotificationServiceImpl;
import team.washer.server.v2.domain.user.entity.User;

@ExtendWith(MockitoExtension.class)
@DisplayName("SendFcmNotificationServiceImpl 클래스의")
class SendFcmNotificationServiceTest {

    @InjectMocks
    private SendFcmNotificationServiceImpl sendFcmNotificationService;

    @Mock
    private FirebaseMessaging firebaseMessaging;

    @Mock
    private DeleteFcmTokenService deleteFcmTokenService;

    @Mock
    private User user;

    @Nested
    @DisplayName("execute 메서드는")
    class Describe_execute {

        @Nested
        @DisplayName("유효한 FCM 토큰이 있는 사용자에게 알림을 전송할 때")
        class Context_with_valid_token {

            @Test
            @DisplayName("FCM 메시지를 전송해야 한다")
            void it_sends_fcm_message() throws Exception {
                // Given
                given(user.getFcmToken()).willReturn("valid-fcm-token");
                given(user.getId()).willReturn(1L);
                given(firebaseMessaging.send(any(Message.class))).willReturn("message-id-123");

                // When
                sendFcmNotificationService.execute(user, "세탁 완료 알림", "W-3F-L1 세탁이 완료되었습니다.");

                // Then
                then(firebaseMessaging).should(times(1)).send(any(Message.class));
                then(deleteFcmTokenService).should(never()).execute(anyLong());
            }
        }

        @Nested
        @DisplayName("FCM 토큰이 null인 사용자에게 알림을 전송할 때")
        class Context_with_null_token {

            @Test
            @DisplayName("FCM 메시지를 전송하지 않아야 한다")
            void it_skips_notification() throws Exception {
                // Given
                given(user.getFcmToken()).willReturn(null);

                // When
                sendFcmNotificationService.execute(user, "세탁 완료 알림", "W-3F-L1 세탁이 완료되었습니다.");

                // Then
                then(firebaseMessaging).should(never()).send(any(Message.class));
                then(deleteFcmTokenService).should(never()).execute(anyLong());
            }
        }

        @Nested
        @DisplayName("FCM 토큰이 빈 문자열인 사용자에게 알림을 전송할 때")
        class Context_with_blank_token {

            @Test
            @DisplayName("FCM 메시지를 전송하지 않아야 한다")
            void it_skips_notification() throws Exception {
                // Given
                given(user.getFcmToken()).willReturn("   ");

                // When
                sendFcmNotificationService.execute(user, "세탁 완료 알림", "W-3F-L1 세탁이 완료되었습니다.");

                // Then
                then(firebaseMessaging).should(never()).send(any(Message.class));
                then(deleteFcmTokenService).should(never()).execute(anyLong());
            }
        }

        @Nested
        @DisplayName("UNREGISTERED 에러로 FCM 전송이 실패할 때")
        class Context_with_unregistered_error {

            @Test
            @DisplayName("만료된 FCM 토큰을 삭제해야 한다")
            void it_deletes_unregistered_token() throws Exception {
                // Given
                Long userId = 1L;
                given(user.getFcmToken()).willReturn("expired-fcm-token");
                given(user.getId()).willReturn(userId);

                FirebaseMessagingException exception = mock(FirebaseMessagingException.class);
                given(exception.getMessagingErrorCode()).willReturn(MessagingErrorCode.UNREGISTERED);
                given(firebaseMessaging.send(any(Message.class))).willThrow(exception);

                // When
                sendFcmNotificationService.execute(user, "세탁 완료 알림", "W-3F-L1 세탁이 완료되었습니다.");

                // Then
                then(deleteFcmTokenService).should(times(1)).execute(userId);
            }
        }

        @Nested
        @DisplayName("INVALID_ARGUMENT 에러로 FCM 전송이 실패할 때")
        class Context_with_invalid_argument_error {

            @Test
            @DisplayName("잘못된 형식의 FCM 토큰을 삭제해야 한다")
            void it_deletes_malformed_token() throws Exception {
                // Given
                Long userId = 1L;
                given(user.getFcmToken()).willReturn("malformed-fcm-token");
                given(user.getId()).willReturn(userId);

                FirebaseMessagingException exception = mock(FirebaseMessagingException.class);
                given(exception.getMessagingErrorCode()).willReturn(MessagingErrorCode.INVALID_ARGUMENT);
                given(firebaseMessaging.send(any(Message.class))).willThrow(exception);

                // When
                sendFcmNotificationService.execute(user, "세탁 완료 알림", "W-3F-L1 세탁이 완료되었습니다.");

                // Then
                then(deleteFcmTokenService).should(times(1)).execute(userId);
            }
        }

        @Nested
        @DisplayName("일시적 에러(INTERNAL)로 FCM 전송이 실패할 때")
        class Context_with_internal_error {

            @Test
            @DisplayName("FCM 토큰을 삭제하지 않아야 한다")
            void it_does_not_delete_token() throws Exception {
                // Given
                Long userId = 1L;
                given(user.getFcmToken()).willReturn("valid-fcm-token");
                given(user.getId()).willReturn(userId);

                FirebaseMessagingException exception = mock(FirebaseMessagingException.class);
                given(exception.getMessagingErrorCode()).willReturn(MessagingErrorCode.INTERNAL);
                given(firebaseMessaging.send(any(Message.class))).willThrow(exception);

                // When
                sendFcmNotificationService.execute(user, "세탁 완료 알림", "W-3F-L1 세탁이 완료되었습니다.");

                // Then
                then(deleteFcmTokenService).should(never()).execute(anyLong());
            }
        }
    }
}
