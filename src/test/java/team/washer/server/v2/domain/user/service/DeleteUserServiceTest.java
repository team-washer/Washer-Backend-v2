package team.washer.server.v2.domain.user.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import team.themoment.sdk.exception.ExpectedException;
import team.washer.server.v2.domain.machine.entity.Machine;
import team.washer.server.v2.domain.machine.enums.MachineAvailability;
import team.washer.server.v2.domain.machine.enums.MachineStatus;
import team.washer.server.v2.domain.machine.enums.MachineType;
import team.washer.server.v2.domain.machine.enums.Position;
import team.washer.server.v2.domain.machine.repository.MachineRepository;
import team.washer.server.v2.domain.reservation.entity.Reservation;
import team.washer.server.v2.domain.reservation.enums.ReservationStatus;
import team.washer.server.v2.domain.reservation.repository.ReservationRepository;
import team.washer.server.v2.domain.reservation.support.UserReservationCleanupSupport;
import team.washer.server.v2.domain.user.entity.User;
import team.washer.server.v2.domain.user.repository.UserRepository;
import team.washer.server.v2.domain.user.service.impl.DeleteUserServiceImpl;
import team.washer.server.v2.global.util.DateTimeUtil;

@ExtendWith(MockitoExtension.class)
@DisplayName("DeleteUserServiceImpl 클래스의")
class DeleteUserServiceTest {

    private static final List<ReservationStatus> ACTIVE_STATUSES = List.of(ReservationStatus.RESERVED,
            ReservationStatus.RUNNING);
    private static final int TIMEOUT_MINUTES = ReservationStatus.RESERVED.getTimeoutMinutes();

    private DeleteUserServiceImpl deleteUserService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private MachineRepository machineRepository;

    // 예약 취소와 기기 해제가 실제로 일어나는지 검증하기 위해 Support는 실제 구현체를 사용한다
    @BeforeEach
    void setUp() {
        final var userReservationCleanupSupport = new UserReservationCleanupSupport(reservationRepository,
                machineRepository);
        deleteUserService = new DeleteUserServiceImpl(userRepository, userReservationCleanupSupport);
    }

    private User createUser() {
        return User.builder().name("김철수").studentId("20210001").roomNumber("301").grade(3).floor(3).penaltyCount(0)
                .build();
    }

    private Machine createReservedMachine() {
        return Machine.builder().name("W-2F-L1").type(MachineType.WASHER).deviceId("device-1").floor(2)
                .position(Position.LEFT).number(1).status(MachineStatus.NORMAL)
                .availability(MachineAvailability.RESERVED).build();
    }

    private Reservation createReservation(final User user,
            final Machine machine,
            final ReservationStatus status,
            final long reservedMinutesAgo) {
        return Reservation.builder().user(user).machine(machine)
                .reservedAt(DateTimeUtil.nowInKorea().minusMinutes(reservedMinutesAgo)).status(status).build();
    }

    private Reservation createExpiredReservation(final User user, final Machine machine) {
        return createReservation(user, machine, ReservationStatus.RESERVED, TIMEOUT_MINUTES + 5);
    }

    @Nested
    @DisplayName("execute 메서드는")
    class Describe_execute {

        @Nested
        @DisplayName("활성 상태 예약이 없는 사용자를 삭제할 때")
        class Context_without_active_reservations {

            @Test
            @DisplayName("사용자 행을 잠근 뒤 사용자를 삭제해야 한다")
            void it_deletes_user() {
                // Given
                Long userId = 1L;
                User user = createUser();
                given(userRepository.findByIdForUpdate(userId)).willReturn(Optional.of(user));

                // When
                deleteUserService.execute(userId);

                // Then
                then(userRepository).should(times(1)).findByIdForUpdate(userId);
                then(reservationRepository).should(times(1)).findByUserAndStatusInForUpdate(user, ACTIVE_STATUSES);
                then(userRepository).should(times(1)).delete(user);
            }
        }

        @Nested
        @DisplayName("잠금 조회한 예약 중 만료되지 않은 RESERVED 예약이 있을 때")
        class Context_with_reserved_reservation {

            @Test
            @DisplayName("ExpectedException을 던지고 예약을 취소하지 않아야 한다")
            void it_throws_expected_exception() {
                // Given
                Long userId = 1L;
                User user = createUser();
                Machine machine = createReservedMachine();
                Reservation reserved = createReservation(user, machine, ReservationStatus.RESERVED, 1);
                given(userRepository.findByIdForUpdate(userId)).willReturn(Optional.of(user));
                given(reservationRepository.findByUserAndStatusInForUpdate(user, ACTIVE_STATUSES))
                        .willReturn(List.of(reserved));

                // When & Then
                assertThatThrownBy(() -> deleteUserService.execute(userId)).isInstanceOf(ExpectedException.class)
                        .hasMessage("활성 예약이 있는 사용자는 삭제할 수 없습니다")
                        .hasFieldOrPropertyWithValue("statusCode", HttpStatus.BAD_REQUEST);

                assertThat(reserved.getStatus()).isEqualTo(ReservationStatus.RESERVED);
                assertThat(machine.getAvailability()).isEqualTo(MachineAvailability.RESERVED);
                then(machineRepository).should(never()).saveAll(anyIterable());
                then(userRepository).should(never()).delete(any(User.class));
            }
        }

        @Nested
        @DisplayName("잠금 조회한 예약 중 RUNNING 예약이 있을 때")
        class Context_with_running_reservation {

            @Test
            @DisplayName("ExpectedException을 던져야 한다")
            void it_throws_expected_exception() {
                // Given
                Long userId = 1L;
                User user = createUser();
                Reservation running = createReservation(user,
                        createReservedMachine(),
                        ReservationStatus.RUNNING,
                        TIMEOUT_MINUTES + 60);
                given(userRepository.findByIdForUpdate(userId)).willReturn(Optional.of(user));
                given(reservationRepository.findByUserAndStatusInForUpdate(user, ACTIVE_STATUSES))
                        .willReturn(List.of(running));

                // When & Then
                assertThatThrownBy(() -> deleteUserService.execute(userId)).isInstanceOf(ExpectedException.class)
                        .hasMessage("활성 예약이 있는 사용자는 삭제할 수 없습니다")
                        .hasFieldOrPropertyWithValue("statusCode", HttpStatus.BAD_REQUEST);

                then(userRepository).should(never()).delete(any(User.class));
            }
        }

        @Nested
        @DisplayName("타임아웃이 지난 RESERVED 예약만 남은 사용자를 삭제할 때")
        class Context_with_expired_reservations_only {

            @Test
            @DisplayName("예약을 취소하고 기기를 AVAILABLE 상태로 되돌린 뒤 사용자를 삭제해야 한다")
            void it_releases_machine_and_deletes_user() {
                // Given
                Long userId = 1L;
                User user = createUser();
                Machine machine = createReservedMachine();
                Reservation expired = createExpiredReservation(user, machine);

                given(userRepository.findByIdForUpdate(userId)).willReturn(Optional.of(user));
                given(reservationRepository.findByUserAndStatusInForUpdate(user, ACTIVE_STATUSES))
                        .willReturn(List.of(expired));

                // When
                deleteUserService.execute(userId);

                // Then
                assertThat(expired.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
                assertThat(machine.getAvailability()).isEqualTo(MachineAvailability.AVAILABLE);
                then(machineRepository).should(times(1)).saveAll(anyIterable());
                then(userRepository).should(times(1)).delete(user);
            }

            @Test
            @DisplayName("예약이 점유한 기기를 ID 오름차순으로 먼저 잠근 뒤 예약을 잠가야 한다")
            void it_locks_machines_before_reservations() {
                // Given
                Long userId = 1L;
                User user = createUser();
                given(userRepository.findByIdForUpdate(userId)).willReturn(Optional.of(user));
                given(reservationRepository.findMachineIdsByUserAndStatusIn(user, ACTIVE_STATUSES))
                        .willReturn(List.of(20L, 10L));

                // When
                deleteUserService.execute(userId);

                // Then
                var inOrder = inOrder(userRepository, machineRepository, reservationRepository);
                inOrder.verify(userRepository).findByIdForUpdate(userId);
                inOrder.verify(machineRepository).findByIdForUpdate(10L);
                inOrder.verify(machineRepository).findByIdForUpdate(20L);
                inOrder.verify(reservationRepository).findByUserAndStatusInForUpdate(user, ACTIVE_STATUSES);
            }
        }

        @Nested
        @DisplayName("기기 락을 기다리는 동안 다른 사용자의 예약이 같은 기기에 커밋되었을 때")
        class Context_with_other_users_reservation_on_same_machine {

            @Test
            @DisplayName("만료 예약만 취소하고 기기는 다른 사용자의 예약 상태로 유지해야 한다")
            void it_keeps_machine_held_by_other_reservation() {
                // Given
                Long userId = 1L;
                User user = createUser();
                User otherUser = User.builder().name("이영희").studentId("20210002").roomNumber("302").grade(3).floor(3)
                        .penaltyCount(0).build();
                Machine machine = createReservedMachine();
                Reservation expired = createExpiredReservation(user, machine);
                Reservation otherReservation = createReservation(otherUser, machine, ReservationStatus.RESERVED, 0);

                given(userRepository.findByIdForUpdate(userId)).willReturn(Optional.of(user));
                given(reservationRepository.findByUserAndStatusInForUpdate(user, ACTIVE_STATUSES))
                        .willReturn(List.of(expired));
                given(reservationRepository.findByMachineAndStatusInForUpdate(machine, ACTIVE_STATUSES))
                        .willReturn(List.of(expired, otherReservation));

                // When
                deleteUserService.execute(userId);

                // Then
                assertThat(expired.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
                assertThat(otherReservation.getStatus()).isEqualTo(ReservationStatus.RESERVED);
                assertThat(machine.getAvailability()).isEqualTo(MachineAvailability.RESERVED);
                then(userRepository).should(times(1)).delete(user);
            }
        }

        @Nested
        @DisplayName("고장 처리된 기기에 만료 예약만 남은 사용자를 삭제할 때")
        class Context_with_expired_reservation_on_malfunction_machine {

            @Test
            @DisplayName("예약만 취소하고 기기는 사용 불가 상태로 유지해야 한다")
            void it_cancels_reservation_and_keeps_machine_unavailable() {
                // Given
                Long userId = 1L;
                User user = createUser();
                Machine machine = createReservedMachine();
                machine.markAsMalfunction();
                Reservation expired = createExpiredReservation(user, machine);

                given(userRepository.findByIdForUpdate(userId)).willReturn(Optional.of(user));
                given(reservationRepository.findByUserAndStatusInForUpdate(user, ACTIVE_STATUSES))
                        .willReturn(List.of(expired));

                // When
                deleteUserService.execute(userId);

                // Then
                assertThat(expired.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
                assertThat(machine.getStatus()).isEqualTo(MachineStatus.MALFUNCTION);
                assertThat(machine.getAvailability()).isEqualTo(MachineAvailability.UNAVAILABLE);
                then(userRepository).should(times(1)).delete(user);
            }
        }

        @Nested
        @DisplayName("존재하지 않는 사용자 ID로 요청할 때")
        class Context_with_nonexistent_user_id {

            @Test
            @DisplayName("ExpectedException을 던져야 한다")
            void it_throws_expected_exception() {
                // Given
                Long userId = 999L;

                given(userRepository.findByIdForUpdate(userId)).willReturn(Optional.empty());

                // When & Then
                assertThatThrownBy(() -> deleteUserService.execute(userId)).isInstanceOf(ExpectedException.class)
                        .hasMessage("사용자를 찾을 수 없습니다").hasFieldOrPropertyWithValue("statusCode", HttpStatus.NOT_FOUND);

                then(reservationRepository).should(never()).findByUserAndStatusInForUpdate(any(User.class), anyList());
                then(userRepository).should(never()).delete(any(User.class));
            }
        }
    }
}
