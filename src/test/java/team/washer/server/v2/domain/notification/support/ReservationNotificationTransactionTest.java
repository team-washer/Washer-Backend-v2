package team.washer.server.v2.domain.notification.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willReturn;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.hibernate.LazyInitializationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
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
    private JdbcTemplate jdbcTemplate;
    private ListAppender<ILoggingEvent> logAppender;

    @BeforeEach
    void setUp() throws Exception {
        final var dataSource = new DriverManagerDataSource("jdbc:h2:mem:reservation-notification;DB_CLOSE_DELAY=-1",
                "sa",
                "");
        transactionManager = new DataSourceTransactionManager(dataSource);
        transactionTemplate = new TransactionTemplate(transactionManager);
        jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS lifecycle_transition (reservation_id BIGINT PRIMARY KEY, "
                + "status VARCHAR(20) NOT NULL)");
        jdbcTemplate.update("DELETE FROM lifecycle_transition");
        reservationNotificationSupport = createTransactionalReservationNotificationSupport();
        // lifecycle 전이 알림은 호출자 트랜잭션이 롤백되면 저장 자체를 하지 않으므로 저장 stub이 쓰이지 않는 시나리오가 있다
        lenient().when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> {
            saveConnectionHolder = currentConnectionHolder();
            return invocation.getArgument(0);
        });
        lenient().when(notificationRepository.countByUser(any(User.class))).thenReturn(1L);
        lenient().when(firebaseMessaging.send(any(Message.class))).thenReturn("message-id");
        logAppender = new ListAppender<>();
        logAppender.start();
        supportLogger().addAppender(logAppender);
    }

    @AfterEach
    void tearDown() {
        supportLogger().detachAppender(logAppender);
    }

    private Logger supportLogger() {
        return (Logger) LoggerFactory.getLogger(ReservationNotificationSupport.class);
    }

    private ReservationNotificationSupport createTransactionalReservationNotificationSupport() {
        final var fcmNotificationSupport = new FcmNotificationSupport(firebaseMessaging,
                deleteFcmTokenIfMatchesService,
                new SyncTaskExecutor());
        afterCommitExecutor = new AfterCommitExecutor(transactionManager);
        final var target = new ReservationNotificationSupport(notificationRepository,
                fcmNotificationSupport,
                afterCommitExecutor,
                transactionManager);
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
                reservationNotificationSupport.sendForceStop(user, createMachine());
                status.setRollbackOnly();
            });

            // Then
            then(firebaseMessaging).shouldHaveNoInteractions();
        }
    }

    @Nested
    @DisplayName("lifecycle 전이 알림은")
    class Context_with_lifecycle_notification {

        @ParameterizedTest
        @EnumSource(LifecycleNotification.class)
        @DisplayName("호출자 트랜잭션이 커밋된 뒤 독립 트랜잭션에서 한 번만 저장하고 전송해야 한다")
        void persists_once_in_separate_transaction_after_caller_commit(final LifecycleNotification notification)
                throws Exception {
            // Given
            final var user = createUser();
            final var machine = createMachine();
            final var outerConnectionHolder = new AtomicReference<Object>();

            // When
            transactionTemplate.executeWithoutResult(status -> {
                outerConnectionHolder.set(currentConnectionHolder());
                notification.send(reservationNotificationSupport, user, machine);
                then(notificationRepository).shouldHaveNoInteractions();
            });

            // Then
            assertThat(saveConnectionHolder).isNotNull().isNotSameAs(outerConnectionHolder.get());
            then(notificationRepository).should(times(1)).save(any(Notification.class));
            then(firebaseMessaging).should(times(1)).send(any(Message.class));
        }

        @ParameterizedTest
        @EnumSource(LifecycleNotification.class)
        @DisplayName("호출자 트랜잭션이 롤백되면 저장하지도 전송하지도 않아야 한다")
        void does_not_persist_when_caller_rolls_back(final LifecycleNotification notification) {
            // Given
            final var user = createUser();
            final var machine = createMachine();

            // When
            transactionTemplate.executeWithoutResult(status -> {
                notification.send(reservationNotificationSupport, user, machine);
                status.setRollbackOnly();
            });

            // Then
            then(notificationRepository).shouldHaveNoInteractions();
            then(firebaseMessaging).shouldHaveNoInteractions();
        }

        @ParameterizedTest
        @MethodSource("lifecyclePersistFailures")
        @DisplayName("저장이나 보관 개수 정리가 실패해도 호출자의 상태 전이를 커밋하고 실패를 로그로 남겨야 한다")
        void commits_caller_transition_when_persist_fails(final LifecycleNotification notification,
                final PersistFailure failure) {
            // Given
            final var user = createUser();
            final var machine = createMachine();
            failure.inject(notificationRepository);

            // When
            assertThatCode(() -> transactionTemplate.executeWithoutResult(status -> {
                jdbcTemplate.update("INSERT INTO lifecycle_transition (reservation_id, status) VALUES (1, 'DONE')");
                notification.send(reservationNotificationSupport, user, machine);
            })).doesNotThrowAnyException();

            // Then
            assertThat(jdbcTemplate.queryForObject("SELECT status FROM lifecycle_transition WHERE reservation_id = 1",
                    String.class)).isEqualTo("DONE");
            then(firebaseMessaging).shouldHaveNoInteractions();
            assertThat(logAppender.list).filteredOn(event -> event.getLevel() == Level.ERROR)
                    .extracting(ILoggingEvent::getFormattedMessage).singleElement().asString()
                    .contains("event=notification_persist_failed", "type=" + notification.name(), "phase=persist");
        }

        @ParameterizedTest
        @EnumSource(LifecycleNotification.class)
        @DisplayName("알림 준비 단계가 실패해도 호출자의 상태 전이를 커밋하고 실패를 로그로 남겨야 한다")
        void commits_caller_transition_when_preparation_fails(final LifecycleNotification notification) {
            // Given
            final var user = createUser();
            final var machine = mock(Machine.class);
            given(machine.getName()).willThrow(new LazyInitializationException("could not initialize proxy"));

            // When
            assertThatCode(() -> transactionTemplate.executeWithoutResult(status -> {
                jdbcTemplate.update("INSERT INTO lifecycle_transition (reservation_id, status) VALUES (1, 'DONE')");
                notification.send(reservationNotificationSupport, user, machine);
            })).doesNotThrowAnyException();

            // Then
            assertThat(jdbcTemplate.queryForObject("SELECT status FROM lifecycle_transition WHERE reservation_id = 1",
                    String.class)).isEqualTo("DONE");
            then(notificationRepository).shouldHaveNoInteractions();
            then(firebaseMessaging).shouldHaveNoInteractions();
            assertThat(logAppender.list).filteredOn(event -> event.getLevel() == Level.ERROR)
                    .extracting(ILoggingEvent::getFormattedMessage).singleElement().asString()
                    .contains("event=notification_persist_failed", "type=" + notification.name(), "phase=prepare");
        }

        static Stream<Arguments> lifecyclePersistFailures() {
            return Stream.of(LifecycleNotification.values()).flatMap(notification -> Stream.of(PersistFailure.values())
                    .map(failure -> Arguments.of(notification, failure)));
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

    /**
     * 호출자 트랜잭션과 분리되는 lifecycle 전이 알림. 상수 이름은 저장되는 알림 유형과 같다.
     */
    private enum LifecycleNotification {
        STARTED {
            @Override
            void send(final ReservationNotificationSupport support, final User user, final Machine machine) {
                support.sendStarted(user, machine, LocalDateTime.of(2026, 10, 6, 12, 0));
            }
        },
        COMPLETION {
            @Override
            void send(final ReservationNotificationSupport support, final User user, final Machine machine) {
                support.sendCompletion(user, machine);
            }
        },
        INTERRUPTION {
            @Override
            void send(final ReservationNotificationSupport support, final User user, final Machine machine) {
                support.sendInterruption(user, machine);
            }
        },
        PAUSE_TIMEOUT {
            @Override
            void send(final ReservationNotificationSupport support, final User user, final Machine machine) {
                support.sendPauseTimeout(user, machine);
            }
        };

        abstract void send(ReservationNotificationSupport support, User user, Machine machine);
    }

    /**
     * 알림 영속화 단계별 실패 주입 지점.
     */
    private enum PersistFailure {
        SAVE {
            @Override
            void inject(final NotificationRepository repository) {
                willThrow(new DataIntegrityViolationException("save failed")).given(repository)
                        .save(any(Notification.class));
            }
        },
        COUNT {
            @Override
            void inject(final NotificationRepository repository) {
                willThrow(new QueryTimeoutException("count failed")).given(repository).countByUser(any(User.class));
            }
        },
        LIMIT_CLEANUP {
            @Override
            void inject(final NotificationRepository repository) {
                willReturn(31L).given(repository).countByUser(any(User.class));
                willThrow(new CannotAcquireLockException("delete failed")).given(repository)
                        .deleteOldestByUserExceedingLimit(any(User.class), anyInt());
            }
        };

        abstract void inject(NotificationRepository repository);
    }
}
