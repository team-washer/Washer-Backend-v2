package team.washer.server.v2.domain.notification.support;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;

import team.washer.server.v2.domain.notification.service.DeleteFcmTokenIfMatchesService;
import team.washer.server.v2.domain.user.entity.User;
import team.washer.server.v2.global.config.AsyncConfig;

@ExtendWith(MockitoExtension.class)
@DisplayName("FcmNotificationSupport 클래스는")
class FcmNotificationSupportTest {

    @InjectMocks
    private FcmNotificationSupport fcmNotificationSupport;

    @Mock
    private FirebaseMessaging firebaseMessaging;

    @Mock
    private DeleteFcmTokenIfMatchesService deleteFcmTokenIfMatchesService;

    @Spy
    private TaskExecutor fcmTaskExecutor = new SyncTaskExecutor();

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
        @DisplayName("무효 토큰 오류가 발생해도 전달받은 사용자 엔티티는 변경하지 않아야 한다")
        void it_does_not_mutate_user_entity_on_invalid_token_failure() throws Exception {
            // Given
            final User user = createUserWithToken();
            final FirebaseMessagingException exception = mock(FirebaseMessagingException.class);
            given(exception.getMessagingErrorCode()).willReturn(MessagingErrorCode.UNREGISTERED);
            given(firebaseMessaging.send(any(Message.class))).willThrow(exception);

            // When
            fcmNotificationSupport.send(user, "제목", "본문");

            // Then
            assertThat(user.getFcmToken()).isEqualTo("fcm-token");
        }

        @Test
        @DisplayName("트랜잭션 안에서 호출되어도 즉시 전송해야 한다")
        void it_sends_immediately_when_transaction_is_active() throws Exception {
            // Given
            final User user = createUserWithToken();
            given(firebaseMessaging.send(any(Message.class))).willReturn("message-id");
            TransactionSynchronizationManager.setActualTransactionActive(true);
            TransactionSynchronizationManager.initSynchronization();

            // When
            fcmNotificationSupport.send(user, "제목", "본문");

            // Then
            then(firebaseMessaging).should(times(1)).send(any(Message.class));
            assertThat(TransactionSynchronizationManager.getSynchronizations()).isEmpty();
        }

        @Test
        @DisplayName("트랜잭션 안에서 전송 중 런타임 예외가 발생해도 호출자에게 전파하지 않아야 한다")
        void it_does_not_propagate_runtime_exception_when_transaction_is_active() throws Exception {
            // Given
            final User user = createUserWithToken();
            willThrow(new IllegalStateException("Firebase unavailable")).given(firebaseMessaging)
                    .send(any(Message.class));
            TransactionSynchronizationManager.setActualTransactionActive(true);
            TransactionSynchronizationManager.initSynchronization();

            // When
            fcmNotificationSupport.send(user, "제목", "본문");

            // Then
            assertThat(TransactionSynchronizationManager.getSynchronizations()).isEmpty();
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
            assertThat(user.getFcmToken()).isNull();
        }
    }

    @Nested
    @DisplayName("전용 실행기로 전송할 때는")
    class Describe_dedicated_executor {

        private ThreadPoolTaskExecutor executor;

        @AfterEach
        void shutdownExecutor() {
            if (executor != null) {
                executor.shutdown();
            }
        }

        private FcmNotificationSupport createSupport(final ThreadPoolTaskExecutor taskExecutor) {
            executor = taskExecutor;
            executor.initialize();
            return new FcmNotificationSupport(firebaseMessaging, deleteFcmTokenIfMatchesService, executor);
        }

        private ThreadPoolTaskExecutor createSingleSlotExecutor() {
            final var taskExecutor = new ThreadPoolTaskExecutor();
            taskExecutor.setCorePoolSize(1);
            taskExecutor.setMaxPoolSize(1);
            taskExecutor.setQueueCapacity(1);
            taskExecutor.setWaitForTasksToCompleteOnShutdown(true);
            taskExecutor.setAwaitTerminationSeconds(5);
            return taskExecutor;
        }

        @Test
        @DisplayName("Firebase 호출을 호출 스레드가 아닌 FCM 전용 스레드에서 수행해야 한다")
        void it_sends_on_dedicated_thread() throws Exception {
            // Given
            final var support = createSupport(new AsyncConfig().fcmTaskExecutor());
            final var sendingThreadName = new CompletableFuture<String>();
            given(firebaseMessaging.send(any(Message.class))).willAnswer(invocation -> {
                sendingThreadName.complete(Thread.currentThread().getName());
                return "message-id";
            });

            // When
            support.send(createUserWithToken(), "제목", "본문");

            // Then
            assertThat(sendingThreadName.get(5, TimeUnit.SECONDS)).startsWith("Fcm-")
                    .isNotEqualTo(Thread.currentThread().getName());
        }

        @Test
        @DisplayName("Firebase 응답이 지연되어도 호출자를 기다리게 하지 않아야 한다")
        void it_does_not_block_caller_while_firebase_is_slow() throws Exception {
            // Given
            final var support = createSupport(createSingleSlotExecutor());
            final var firebaseEntered = new CountDownLatch(1);
            final var releaseFirebase = new CountDownLatch(1);
            given(firebaseMessaging.send(any(Message.class))).willAnswer(invocation -> {
                firebaseEntered.countDown();
                releaseFirebase.await(5, TimeUnit.SECONDS);
                return "message-id";
            });

            // When
            support.send(createUserWithToken(), "제목", "본문");

            // Then
            assertThat(firebaseEntered.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(releaseFirebase.getCount()).isEqualTo(1);
            releaseFirebase.countDown();
        }

        @Test
        @DisplayName("대기열이 가득 차면 예외 없이 초과 전송을 버려야 한다")
        void it_drops_notification_when_queue_is_full() throws Exception {
            // Given
            final var support = createSupport(createSingleSlotExecutor());
            final var releaseFirebase = new CountDownLatch(1);
            given(firebaseMessaging.send(any(Message.class))).willAnswer(invocation -> {
                releaseFirebase.await(5, TimeUnit.SECONDS);
                return "message-id";
            });
            final User user = createUserWithToken();

            // When & Then
            assertThatCode(() -> {
                support.send(user, "실행 중", "본문");
                support.send(user, "대기 중", "본문");
                support.send(user, "초과", "본문");
            }).doesNotThrowAnyException();

            releaseFirebase.countDown();
            executor.shutdown();
            then(firebaseMessaging).should(times(2)).send(any(Message.class));
        }

        @Test
        @DisplayName("실행기가 종료된 뒤에는 예외 없이 전송을 버려야 한다")
        void it_drops_notification_after_executor_shutdown() throws Exception {
            // Given
            final var support = createSupport(createSingleSlotExecutor());
            executor.shutdown();

            // When & Then
            assertThatCode(() -> support.send(createUserWithToken(), "제목", "본문")).doesNotThrowAnyException();
            then(firebaseMessaging).should(never()).send(any(Message.class));
        }
    }
}
