package team.washer.server.v2.domain.notification.support;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;

import java.time.LocalDateTime;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;

import team.washer.server.v2.domain.machine.entity.Machine;
import team.washer.server.v2.domain.machine.enums.MachineType;
import team.washer.server.v2.domain.notification.entity.Notification;
import team.washer.server.v2.domain.notification.repository.NotificationRepository;
import team.washer.server.v2.domain.notification.service.DeleteFcmTokenIfMatchesService;
import team.washer.server.v2.domain.user.entity.User;

@MockitoSettings
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
    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void setUp() throws Exception {
        final var dataSource = new DriverManagerDataSource("jdbc:h2:mem:reservation-notification;DB_CLOSE_DELAY=-1",
                "sa",
                "");
        transactionTemplate = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        final var fcmNotificationSupport = new FcmNotificationSupport(firebaseMessaging,
                deleteFcmTokenIfMatchesService);
        reservationNotificationSupport = new ReservationNotificationSupport(notificationRepository,
                fcmNotificationSupport);
        given(notificationRepository.save(any(Notification.class))).willAnswer(invocation -> invocation.getArgument(0));
        given(notificationRepository.countByUser(any(User.class))).willReturn(1L);
        lenient().when(firebaseMessaging.send(any(Message.class))).thenReturn("message-id");
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
            final var user = createUser();
            final var machine = createMachine();

            transactionTemplate.executeWithoutResult(status -> IntStream.range(0, notificationCount)
                    .forEach(ignored -> reservationNotificationSupport.sendCompletion(user, machine)));

            then(firebaseMessaging).should(times(notificationCount)).send(any(Message.class));
        }

        @Test
        @DisplayName("모든 예약 알림 종류를 정확히 한 번씩 전송해야 한다")
        void sends_every_reservation_notification_once() throws Exception {
            final var user = createUser();
            final var machine = createMachine();

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

            then(firebaseMessaging).should(times(10)).send(any(Message.class));
        }
    }

    @Test
    @DisplayName("롤백하면 Firebase 메시지를 전송하지 않아야 한다")
    void does_not_send_when_transaction_rolls_back() {
        final var user = createUser();

        transactionTemplate.executeWithoutResult(status -> {
            reservationNotificationSupport.sendCompletion(user, createMachine());
            status.setRollbackOnly();
        });

        then(firebaseMessaging).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("트랜잭션 밖에서 호출하면 Firebase 메시지를 한 번 전송해야 한다")
    void sends_once_outside_transaction() throws Exception {
        reservationNotificationSupport.sendCompletion(createUser(), createMachine());

        then(firebaseMessaging).should(times(1)).send(any(Message.class));
    }
}
