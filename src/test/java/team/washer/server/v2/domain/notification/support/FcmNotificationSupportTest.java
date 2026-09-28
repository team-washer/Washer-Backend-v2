package team.washer.server.v2.domain.notification.support;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;

import team.washer.server.v2.domain.notification.service.DeleteFcmTokenIfMatchesService;
import team.washer.server.v2.domain.user.entity.User;

@ExtendWith(MockitoExtension.class)
@DisplayName("FcmNotificationSupport 클래스는")
class FcmNotificationSupportTest {

    @InjectMocks
    private FcmNotificationSupport fcmNotificationSupport;

    @Mock
    private FirebaseMessaging firebaseMessaging;

    @Mock
    private DeleteFcmTokenIfMatchesService deleteFcmTokenIfMatchesService;

    @AfterEach
    void clearTransactionSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    private User createUserWithToken() {
        final var user = User.builder().name("김철수").studentId("20210001").roomNumber("301").grade(3).floor(3).build();
        user.updateFcmToken("fcm-token");
        return user;
    }

    private User createUserWithoutToken() {
        return User.builder().name("테스트").studentId("20210001").roomNumber("301").grade(3).floor(3).build();
    }

    @Nested
    @DisplayName("send 메서드는")
    class Describe_send {

        @Test
        @DisplayName("제목이 null이어도 예외 없이 전송해야 한다")
        void it_omits_null_title_data() throws Exception {
            // Given
            User user = createUserWithToken();
            given(firebaseMessaging.send(any(Message.class))).willReturn("message-id");

            // When & Then
            assertThatCode(() -> fcmNotificationSupport.send(user, null, "본문")).doesNotThrowAnyException();
            then(firebaseMessaging).should(times(1)).send(any(Message.class));
        }

        @Test
        @DisplayName("본문이 null이어도 예외 없이 전송해야 한다")
        void it_omits_null_body_data() throws Exception {
            // Given
            User user = createUserWithToken();
            given(firebaseMessaging.send(any(Message.class))).willReturn("message-id");

            // When & Then
            assertThatCode(() -> fcmNotificationSupport.send(user, "제목", null)).doesNotThrowAnyException();
            then(firebaseMessaging).should(times(1)).send(any(Message.class));
        }

        @Test
        @DisplayName("FCM 토큰이 없으면 발송을 시도하지 않아야 한다")
        void it_skips_sending_when_fcm_token_is_missing() throws Exception {
            // Given
            User user = createUserWithoutToken();

            // When & Then
            assertThatCode(() -> fcmNotificationSupport.send(user, "제목", "본문")).doesNotThrowAnyException();
            then(firebaseMessaging).should(never()).send(any(Message.class));
        }

        @Test
        @DisplayName("만료된 토큰 오류가 발생하면 실패한 토큰을 조건부 삭제해야 한다")
        void it_deletes_only_the_failed_token() throws Exception {
            // Given
            final User user = createUserWithToken();
            final FirebaseMessagingException exception = mock(FirebaseMessagingException.class);
            given(exception.getMessagingErrorCode()).willReturn(MessagingErrorCode.UNREGISTERED);
            given(firebaseMessaging.send(any(Message.class))).willThrow(exception);

            // When
            fcmNotificationSupport.send(user, "제목", "본문");

            // Then
            then(deleteFcmTokenIfMatchesService).should(times(1)).execute(any(), eq("fcm-token"));
            assertThat(user.getFcmToken()).isEqualTo("fcm-token");
        }

        @Test
        @DisplayName("토큰 정리 실패가 알림 전송 호출자에게 전파되지 않아야 한다")
        void it_does_not_propagate_token_cleanup_failure() throws Exception {
            // Given
            final User user = createUserWithToken();
            final FirebaseMessagingException exception = mock(FirebaseMessagingException.class);
            given(exception.getMessagingErrorCode()).willReturn(MessagingErrorCode.UNREGISTERED);
            given(firebaseMessaging.send(any(Message.class))).willThrow(exception);
            willThrow(new RuntimeException("database down")).given(deleteFcmTokenIfMatchesService).execute(any(),
                    eq("fcm-token"));

            // When & Then
            assertThatCode(() -> fcmNotificationSupport.send(user, "제목", "본문")).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("트랜잭션 안에서 호출되면 커밋 이후에 전송해야 한다")
        void it_sends_after_transaction_commit_when_transaction_is_active() throws Exception {
            // Given
            final User user = createUserWithToken();
            given(firebaseMessaging.send(any(Message.class))).willReturn("message-id");
            TransactionSynchronizationManager.setActualTransactionActive(true);
            TransactionSynchronizationManager.initSynchronization();

            // When
            fcmNotificationSupport.send(user, "제목", "본문");

            // Then
            then(firebaseMessaging).should(never()).send(any(Message.class));
            final var synchronizations = TransactionSynchronizationManager.getSynchronizations();
            assertThat(synchronizations).hasSize(1);

            synchronizations.getFirst().afterCommit();

            then(firebaseMessaging).should(times(1)).send(any(Message.class));
        }

        @Test
        @DisplayName("커밋 이후 전송에서 런타임 예외가 발생해도 호출자에게 전파하지 않아야 한다")
        void it_does_not_propagate_runtime_exception_after_transaction_commit() throws Exception {
            // Given
            final User user = createUserWithToken();
            willThrow(new IllegalStateException("Firebase unavailable")).given(firebaseMessaging)
                    .send(any(Message.class));
            TransactionSynchronizationManager.setActualTransactionActive(true);
            TransactionSynchronizationManager.initSynchronization();

            // When
            fcmNotificationSupport.send(user, "제목", "본문");

            // Then
            final var synchronizations = TransactionSynchronizationManager.getSynchronizations();
            assertThatCode(() -> synchronizations.getFirst().afterCommit()).doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("sendAndGetMessageId 메서드는")
    class Describe_sendAndGetMessageId {

        @Test
        @DisplayName("트랜잭션이 활성화되어 있어도 즉시 전송하고 메시지 ID를 반환해야 한다")
        void it_sends_immediately_and_returns_message_id() throws Exception {
            // Given
            final User user = createUserWithToken();
            given(firebaseMessaging.send(any(Message.class))).willReturn("message-id");
            TransactionSynchronizationManager.setActualTransactionActive(true);
            TransactionSynchronizationManager.initSynchronization();

            // When
            final String messageId = fcmNotificationSupport.sendAndGetMessageId(user, "제목", "본문");

            // Then
            assertThat(messageId).isEqualTo("message-id");
            then(firebaseMessaging).should(times(1)).send(any(Message.class));
            assertThat(TransactionSynchronizationManager.getSynchronizations()).isEmpty();
        }

        @Test
        @DisplayName("발송에 실패하면 예외를 호출자에게 전파해야 한다")
        void it_propagates_send_failure() throws Exception {
            // Given
            final User user = createUserWithToken();
            final FirebaseMessagingException exception = mock(FirebaseMessagingException.class);
            given(exception.getMessagingErrorCode()).willReturn(MessagingErrorCode.UNAVAILABLE);
            given(firebaseMessaging.send(any(Message.class))).willThrow(exception);

            // When & Then
            assertThatThrownBy(() -> fcmNotificationSupport.sendAndGetMessageId(user, "제목", "본문")).isSameAs(exception);
            then(deleteFcmTokenIfMatchesService).should(never()).execute(any(), any());
        }

        @Test
        @DisplayName("만료된 토큰 오류가 발생하면 예외를 전파하기 전에 토큰을 정리해야 한다")
        void it_deletes_invalid_token_before_propagating() throws Exception {
            // Given
            final User user = createUserWithToken();
            final FirebaseMessagingException exception = mock(FirebaseMessagingException.class);
            given(exception.getMessagingErrorCode()).willReturn(MessagingErrorCode.UNREGISTERED);
            given(firebaseMessaging.send(any(Message.class))).willThrow(exception);

            // When & Then
            assertThatThrownBy(() -> fcmNotificationSupport.sendAndGetMessageId(user, "제목", "본문")).isSameAs(exception);
            then(deleteFcmTokenIfMatchesService).should(times(1)).execute(any(), eq("fcm-token"));
        }
    }
}
