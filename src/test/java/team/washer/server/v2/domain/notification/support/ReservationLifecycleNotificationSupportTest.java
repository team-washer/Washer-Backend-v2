package team.washer.server.v2.domain.notification.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionTemplate;

import com.google.firebase.messaging.FirebaseMessaging;

import team.washer.server.v2.domain.machine.entity.Machine;
import team.washer.server.v2.domain.machine.enums.MachineType;
import team.washer.server.v2.domain.notification.entity.Notification;
import team.washer.server.v2.domain.notification.repository.NotificationRepository;
import team.washer.server.v2.domain.notification.service.DeleteFcmTokenIfMatchesService;
import team.washer.server.v2.domain.user.entity.User;
import team.washer.server.v2.global.common.transaction.AfterCommitExecutor;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReservationLifecycleNotificationSupport 트랜잭션 경계")
class ReservationLifecycleNotificationSupportTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private FirebaseMessaging firebaseMessaging;

    @Mock
    private DeleteFcmTokenIfMatchesService deleteFcmTokenIfMatchesService;

    private TransactionTemplate transactionTemplate;
    private JdbcTemplate jdbcTemplate;
    private ReservationLifecycleNotificationSupport reservationLifecycleNotificationSupport;

    @BeforeEach
    void setUp() {
        final var dataSource = new DriverManagerDataSource("jdbc:h2:mem:lifecycle-notification;DB_CLOSE_DELAY=-1",
                "sa",
                "");
        final var transactionManager = new DataSourceTransactionManager(dataSource);
        transactionTemplate = new TransactionTemplate(transactionManager);
        jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS lifecycle_commit_marker (id BIGINT PRIMARY KEY)");
        jdbcTemplate.execute("DELETE FROM lifecycle_commit_marker");

        final var fcmNotificationSupport = new FcmNotificationSupport(firebaseMessaging,
                deleteFcmTokenIfMatchesService,
                new SyncTaskExecutor());
        final var target = new ReservationNotificationSupport(notificationRepository,
                fcmNotificationSupport,
                new AfterCommitExecutor(transactionManager));
        final var proxyFactory = new ProxyFactory(target);
        proxyFactory.setProxyTargetClass(true);
        proxyFactory
                .addAdvice(new TransactionInterceptor(transactionManager, new AnnotationTransactionAttributeSource()));
        final var transactionalReservationNotificationSupport = (ReservationNotificationSupport) proxyFactory
                .getProxy();
        reservationLifecycleNotificationSupport = new ReservationLifecycleNotificationSupport(
                transactionalReservationNotificationSupport,
                new AfterCommitExecutor(transactionManager));

    }

    @Nested
    @DisplayName("lifecycle 커밋 뒤 알림 저장이 실패하면")
    class Context_when_notification_persistence_fails_after_lifecycle_commit {

        @ParameterizedTest
        @EnumSource(NotificationPersistenceStage.class)
        @DisplayName("핵심 lifecycle 상태는 커밋되고 FCM을 전송하지 않는다")
        void commits_lifecycle_state_without_sending_fcm(final NotificationPersistenceStage stage) throws Exception {
            // Given
            final var user = User.builder().name("테스트 사용자").studentId("20210001").roomNumber("301").grade(3).floor(3)
                    .build();
            final var machine = Machine.builder().name("WASHER-3F-L1").type(MachineType.WASHER).floor(3).build();
            stage.stubFailure(notificationRepository);

            // When
            transactionTemplate.executeWithoutResult(status -> {
                jdbcTemplate.update("INSERT INTO lifecycle_commit_marker (id) VALUES (?)", 1L);
                reservationLifecycleNotificationSupport.sendCompletionAfterCommit(1L, user, machine);
            });

            // Then
            assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM lifecycle_commit_marker", Long.class))
                    .isEqualTo(1L);
            then(firebaseMessaging).shouldHaveNoInteractions();
        }
    }

    @Nested
    @DisplayName("lifecycle 커밋 뒤 알림 저장이 성공하면")
    class Context_when_notification_persistence_succeeds_after_lifecycle_commit {

        @Test
        @DisplayName("알림을 한 번 저장하고 커밋 뒤 FCM을 한 번 전송한다")
        void persists_notification_and_sends_fcm_once() throws Exception {
            // Given
            final var user = User.builder().name("테스트 사용자").studentId("20210001").roomNumber("301").grade(3).floor(3)
                    .build();
            final var machine = Machine.builder().name("WASHER-3F-L1").type(MachineType.WASHER).floor(3).build();
            given(notificationRepository.save(any(Notification.class)))
                    .willAnswer(invocation -> invocation.getArgument(0));
            given(notificationRepository.countByUser(user)).willReturn(1L);

            // When
            transactionTemplate.executeWithoutResult(
                    status -> reservationLifecycleNotificationSupport.sendCompletionAfterCommit(1L, user, machine));

            // Then
            then(notificationRepository).should().save(any(Notification.class));
            then(firebaseMessaging).should().send(any());
        }
    }

    private enum NotificationPersistenceStage {
        SAVE {
            @Override
            void stubFailure(final NotificationRepository notificationRepository) {
                given(notificationRepository.save(any(Notification.class)))
                        .willThrow(new IllegalStateException("db down"));
            }
        },
        COUNT {
            @Override
            void stubFailure(final NotificationRepository notificationRepository) {
                given(notificationRepository.save(any(Notification.class)))
                        .willAnswer(invocation -> invocation.getArgument(0));
                given(notificationRepository.countByUser(any(User.class)))
                        .willThrow(new IllegalStateException("db down"));
            }
        },
        CLEANUP {
            @Override
            void stubFailure(final NotificationRepository notificationRepository) {
                given(notificationRepository.save(any(Notification.class)))
                        .willAnswer(invocation -> invocation.getArgument(0));
                given(notificationRepository.countByUser(any(User.class))).willReturn(31L);
                given(notificationRepository.deleteOldestByUserExceedingLimit(any(User.class), any(Integer.class)))
                        .willThrow(new IllegalStateException("db down"));
            }
        };

        abstract void stubFailure(NotificationRepository notificationRepository);
    }
}
