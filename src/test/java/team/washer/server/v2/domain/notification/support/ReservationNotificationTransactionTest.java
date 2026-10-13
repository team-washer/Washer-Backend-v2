package team.washer.server.v2.domain.notification.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;

import team.washer.server.v2.domain.machine.entity.Machine;
import team.washer.server.v2.domain.machine.enums.MachineType;
import team.washer.server.v2.domain.notification.entity.Notification;
import team.washer.server.v2.domain.notification.repository.NotificationRepository;
import team.washer.server.v2.domain.notification.service.DeleteFcmTokenIfMatchesService;
import team.washer.server.v2.domain.user.entity.User;
import team.washer.server.v2.global.common.transaction.AfterCommitExecutor;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReservationNotificationSupport 트랜잭션 동작은")
class ReservationNotificationTransactionTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private FirebaseMessaging firebaseMessaging;

    @Mock
    private DeleteFcmTokenIfMatchesService deleteFcmTokenIfMatchesService;

    private ReservationNotificationSupport reservationNotificationSupport;
    private AfterCommitExecutor afterCommitExecutor;
    private Object saveConnectionHolder;
    private DataSourceTransactionManager transactionManager;
    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void setUp() throws Exception {
        final var dataSource = new DriverManagerDataSource("jdbc:h2:mem:reservation-notification;DB_CLOSE_DELAY=-1",
                "sa",
                "");
        transactionManager = new DataSourceTransactionManager(dataSource);
        transactionTemplate = new TransactionTemplate(transactionManager);
        reservationNotificationSupport = createTransactionalReservationNotificationSupport();
        given(notificationRepository.save(any(Notification.class))).willAnswer(invocation -> {
            saveConnectionHolder = currentConnectionHolder();
            return invocation.getArgument(0);
        });
        given(notificationRepository.countByUser(any(User.class))).willReturn(1L);
        lenient().when(firebaseMessaging.send(any(Message.class))).thenReturn("message-id");
    }

    private ReservationNotificationSupport createTransactionalReservationNotificationSupport() {
        final var fcmNotificationSupport = new FcmNotificationSupport(firebaseMessaging,
                deleteFcmTokenIfMatchesService,
                new SyncTaskExecutor());
        afterCommitExecutor = new AfterCommitExecutor(transactionManager);
        final var target = new ReservationNotificationSupport(notificationRepository,
                fcmNotificationSupport,
                afterCommitExecutor);
        final var proxyFactory = new ProxyFactory(target);
        proxyFactory.setProxyTargetClass(true);
        proxyFactory
                .addAdvice(new TransactionInterceptor(transactionManager, new AnnotationTransactionAttributeSource()));
        return (ReservationNotificationSupport) proxyFactory.getProxy();
    }

    private User createUser() {
        final var user = User.builder().name("테스트 사용자").studentId("20210001").roomNumber("301").grade(3).floor(3)
                .build();
        user.updateFcmToken("fcm-token");
        return user;
    }

    private Machine createMachine() {
        return Machine.builder().name("WASHER-3F-L1").type(MachineType.WASHER).floor(3).build();
    }

    @Nested
    @DisplayName("성공 커밋하면")
    class Context_with_successful_commit {

        @ParameterizedTest
        @ValueSource(ints = {1, 24, 48})
        @DisplayName("각 예약 완료 알림을 정확히 한 번씩 전송해야 한다")
        void sends_each_completion_notification_once(final int notificationCount) throws Exception {
            // Given
            final var user = createUser();
            final var machine = createMachine();

            // When
            IntStream.range(0, notificationCount).forEach(ignored -> transactionTemplate
                    .executeWithoutResult(status -> reservationNotificationSupport.sendCompletion(user, machine)));

            // Then
            then(firebaseMessaging).should(times(notificationCount)).send(any(Message.class));
        }

        @Test
        @DisplayName("모든 예약 알림 종류를 정확히 한 번씩 전송해야 한다")
        void sends_every_reservation_notification_once() throws Exception {
            // Given
            final var user = createUser();
            final var machine = createMachine();

            // When
            transactionTemplate.executeWithoutResult(status -> {
                reservationNotificationSupport.sendCompletion(user, machine);
                reservationNotificationSupport.sendInterruption(user, machine);
                reservationNotificationSupport.sendForceStop(user, machine);
                reservationNotificationSupport.sendPauseTimeout(user, machine);
                reservationNotificationSupport.sendAutoCancellation(user, machine);
                reservationNotificationSupport.sendStarted(user, machine, LocalDateTime.of(2026, 10, 6, 12, 0));
                reservationNotificationSupport.sendTimeoutWarning(user, machine);
                reservationNotificationSupport.sendCancellationBlock(user, machine);
                reservationNotificationSupport.sendBlockExtension(user, LocalDateTime.of(2026, 10, 8, 12, 0));
                reservationNotificationSupport.sendAdminPenalty(user, "테스트 사유");
            });

            // Then
            then(firebaseMessaging).should(times(10)).send(any(Message.class));
        }
    }

    @Nested
    @DisplayName("롤백하면")
    class Context_with_rollback {

        @Test
        @DisplayName("Firebase 메시지를 전송하지 않아야 한다")
        void does_not_send_when_transaction_rolls_back() {
            // Given
            final var user = createUser();

            // When
            transactionTemplate.executeWithoutResult(status -> {
                reservationNotificationSupport.sendCompletion(user, createMachine());
                status.setRollbackOnly();
            });

            // Then
            then(firebaseMessaging).shouldHaveNoInteractions();
        }
    }

    @Nested
    @DisplayName("트랜잭션 밖에서 호출하면")
    class Context_without_transaction {

        @Test
        @DisplayName("Firebase 메시지를 한 번 전송해야 한다")
        void sends_once_outside_transaction() throws Exception {
            // When
            reservationNotificationSupport.sendCompletion(createUser(), createMachine());

            // Then
            then(firebaseMessaging).should(times(1)).send(any(Message.class));
        }
    }

    @Nested
    @DisplayName("바깥 트랜잭션의 커밋 콜백에서 호출하면")
    class Context_with_after_commit_callback {

        @ParameterizedTest
        @EnumSource(RequiresNewNotification.class)
        @DisplayName("REQUIRES_NEW 알림을 커밋한 뒤 Firebase 메시지를 한 번 전송해야 한다")
        void sends_requires_new_notification_after_outer_commit(final RequiresNewNotification notification)
                throws Exception {
            // Given
            final var user = createUser();
            final var machine = createMachine();

            // When
            transactionTemplate.executeWithoutResult(status -> TransactionSynchronizationManager
                    .registerSynchronization(new TransactionSynchronization() {

                        @Override
                        public void afterCommit() {
                            notification.send(reservationNotificationSupport, user, machine);
                        }
                    }));

            // Then
            then(firebaseMessaging).should(times(1)).send(any(Message.class));
        }

        @Test
        @DisplayName("REQUIRED 알림도 Firebase 메시지를 한 번 전송해야 한다")
        void sends_required_notification_registered_in_raw_after_commit_callback() throws Exception {
            // Given
            final var user = createUser();
            final var machine = createMachine();

            // When
            transactionTemplate.executeWithoutResult(status -> TransactionSynchronizationManager
                    .registerSynchronization(new TransactionSynchronization() {

                        @Override
                        public void afterCommit() {
                            reservationNotificationSupport.sendCompletion(user, machine);
                        }
                    }));

            // Then
            then(firebaseMessaging).should(times(1)).send(any(Message.class));
        }
    }

    @Nested
    @DisplayName("공용 커밋 후 경계의 작업에서 호출하면")
    class Context_with_after_commit_executor_task {

        @Test
        @DisplayName("REQUIRED 알림을 새 트랜잭션에서 저장하고 커밋한 뒤 Firebase 메시지를 한 번 전송해야 한다")
        void persists_required_notification_in_new_transaction_before_sending() throws Exception {
            // Given
            final var user = createUser();
            final var machine = createMachine();
            final var outerConnectionHolder = new AtomicReference<Object>();
            final var committedBeforeSend = new AtomicBoolean();
            given(firebaseMessaging.send(any(Message.class))).willAnswer(invocation -> {
                committedBeforeSend.set(!TransactionSynchronizationManager.isActualTransactionActive());
                return "message-id";
            });

            // When
            transactionTemplate.executeWithoutResult(status -> {
                outerConnectionHolder.set(currentConnectionHolder());
                afterCommitExecutor.execute(() -> reservationNotificationSupport.sendCompletion(user, machine));
            });

            // Then
            assertThat(saveConnectionHolder).isNotNull().isNotSameAs(outerConnectionHolder.get());
            assertThat(committedBeforeSend).isTrue();
            then(firebaseMessaging).should(times(1)).send(any(Message.class));
        }
    }

    private Object currentConnectionHolder() {
        return TransactionSynchronizationManager.getResource(transactionManager.getDataSource());
    }

    private enum RequiresNewNotification {
        TIMEOUT_WARNING {
            @Override
            void send(final ReservationNotificationSupport support, final User user, final Machine machine) {
                support.sendTimeoutWarning(user, machine);
            }
        },
        AUTO_CANCELLATION {
            @Override
            void send(final ReservationNotificationSupport support, final User user, final Machine machine) {
                support.sendAutoCancellation(user, machine);
            }
        },
        CANCELLATION_BLOCK {
            @Override
            void send(final ReservationNotificationSupport support, final User user, final Machine machine) {
                support.sendCancellationBlock(user, machine);
            }
        };

        abstract void send(ReservationNotificationSupport support, User user, Machine machine);
    }
}
