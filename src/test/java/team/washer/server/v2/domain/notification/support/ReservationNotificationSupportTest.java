package team.washer.server.v2.domain.notification.support;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

import java.time.LocalDateTime;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import team.washer.server.v2.domain.machine.entity.Machine;
import team.washer.server.v2.domain.machine.enums.MachineType;
import team.washer.server.v2.domain.notification.entity.Notification;
import team.washer.server.v2.domain.notification.repository.NotificationRepository;
import team.washer.server.v2.domain.user.entity.User;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReservationNotificationSupport 클래스는")
class ReservationNotificationSupportTest {

    @InjectMocks
    private ReservationNotificationSupport reservationNotificationSupport;

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private FcmNotificationSupport fcmNotificationSupport;

    @AfterEach
    void clearTransactionSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    private User createUser() {
        return User.builder().name("김철수").studentId("20210001").roomNumber("301").grade(3).floor(3).build();
    }

    private Machine createMachine() {
        return Machine.builder().name("WASHER-3F-L1").type(MachineType.WASHER).floor(3).build();
    }

    private Machine createMachine(final MachineType machineType) {
        final String machineName = machineType == MachineType.WASHER ? "WASHER-3F-L1" : "DRYER-3F-R1";
        return Machine.builder().name(machineName).type(machineType).floor(3).build();
    }

    @Nested
    @DisplayName("알림 저장 개수 제한 로직은")
    class Describe_notification_limit {

        @Nested
        @DisplayName("알림 개수가 30개 이하이면")
        class Context_with_count_under_limit {

            @Test
            @DisplayName("초과 알림 삭제를 수행하지 않아야 한다")
            void it_does_not_delete_excess() {
                // Given
                User user = createUser();
                Machine machine = createMachine();
                given(notificationRepository.save(any(Notification.class)))
                        .willAnswer(invocation -> invocation.getArgument(0));
                given(notificationRepository.countByUser(user)).willReturn(25L);

                // When
                reservationNotificationSupport.sendCompletion(user, machine);

                // Then
                then(notificationRepository).should(never()).deleteOldestByUserExceedingLimit(any(User.class),
                        anyInt());
            }
        }

        @Nested
        @DisplayName("알림 개수가 정확히 30개이면")
        class Context_with_count_at_limit {

            @Test
            @DisplayName("초과 알림 삭제를 수행하지 않아야 한다")
            void it_does_not_delete_excess() {
                // Given
                User user = createUser();
                Machine machine = createMachine();
                given(notificationRepository.save(any(Notification.class)))
                        .willAnswer(invocation -> invocation.getArgument(0));
                given(notificationRepository.countByUser(user)).willReturn(30L);

                // When
                reservationNotificationSupport.sendCompletion(user, machine);

                // Then
                then(notificationRepository).should(never()).deleteOldestByUserExceedingLimit(any(User.class),
                        anyInt());
            }
        }

        @Nested
        @DisplayName("알림 개수가 30개를 초과하면")
        class Context_with_count_over_limit {

            @Test
            @DisplayName("초과 알림을 삭제해야 한다")
            void it_deletes_excess_notifications() {
                // Given
                User user = createUser();
                Machine machine = createMachine();
                given(notificationRepository.save(any(Notification.class)))
                        .willAnswer(invocation -> invocation.getArgument(0));
                given(notificationRepository.countByUser(user)).willReturn(31L);
                given(notificationRepository.deleteOldestByUserExceedingLimit(user, 30)).willReturn(1);

                // When
                reservationNotificationSupport.sendCompletion(user, machine);

                // Then
                then(notificationRepository).should(times(1)).deleteOldestByUserExceedingLimit(user, 30);
            }
        }
    }

    @Nested
    @DisplayName("시작 알림 메시지는")
    class Describe_started_notification_message {

        @ParameterizedTest
        @EnumSource(MachineType.class)
        @DisplayName("세탁/건조 시작 문구에 조사가 중복되지 않아야 한다")
        void it_does_not_duplicate_particle_for_started_message(final MachineType machineType) {
            // Given
            User user = createUser();
            Machine machine = createMachine(machineType);
            LocalDateTime expectedCompletionTime = LocalDateTime.of(2026, 7, 4, 14, 30);
            given(notificationRepository.save(any(Notification.class)))
                    .willAnswer(invocation -> invocation.getArgument(0));
            given(notificationRepository.countByUser(user)).willReturn(1L);

            // When
            reservationNotificationSupport.sendStarted(user, machine, expectedCompletionTime);

            // Then
            final String expectedBody = machine.getName() + "의 " + machineType.getActionNoun()
                    + " 시작되었습니다.\n예상 완료 시간: 14:30";
            then(fcmNotificationSupport).should(times(1))
                    .send(user, machineType.getDescription() + " 시작 알림", expectedBody);
        }
    }

    @Nested
    @DisplayName("FCM 전송 시점은")
    class Describe_fcm_send_timing {

        @Test
        @DisplayName("트랜잭션 커밋 전에는 전송하지 않고 커밋 이후에 전송해야 한다")
        void it_sends_after_commit() {
            // Given
            User user = createUser();
            Machine machine = createMachine();
            given(notificationRepository.save(any(Notification.class)))
                    .willAnswer(invocation -> invocation.getArgument(0));
            given(notificationRepository.countByUser(user)).willReturn(1L);
            TransactionSynchronizationManager.setActualTransactionActive(true);
            TransactionSynchronizationManager.initSynchronization();

            // When
            reservationNotificationSupport.sendCompletion(user, machine);

            // Then
            then(fcmNotificationSupport).should(never()).send(any(), anyString(), anyString());
            final var synchronizations = TransactionSynchronizationManager.getSynchronizations();
            assertThat(synchronizations).hasSize(1);

            synchronizations.getFirst().afterCommit();

            then(fcmNotificationSupport).should(times(1)).send(eq(user), anyString(), anyString());
        }
    }

    @Nested
    @DisplayName("FCM 전송이 실패할 때는")
    class Describe_fcm_failure {

        @Test
        @DisplayName("알림을 저장하고 예외를 전파하지 않아야 한다")
        void it_persists_notification_without_propagating_exception() {
            // Given
            User user = createUser();
            Machine machine = createMachine();
            given(notificationRepository.save(any(Notification.class)))
                    .willAnswer(invocation -> invocation.getArgument(0));
            given(notificationRepository.countByUser(user)).willReturn(1L);
            willThrow(new RuntimeException("fcm down")).given(fcmNotificationSupport)
                    .send(any(), anyString(), anyString());

            // When & Then
            assertThatCode(() -> reservationNotificationSupport.sendCompletion(user, machine))
                    .doesNotThrowAnyException();
            then(notificationRepository).should(times(1)).save(any(Notification.class));
        }
    }
}
