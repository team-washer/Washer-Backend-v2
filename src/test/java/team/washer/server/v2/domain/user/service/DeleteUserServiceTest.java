package team.washer.server.v2.domain.user.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

import java.time.LocalDateTime;
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

@ExtendWith(MockitoExtension.class)
@DisplayName("DeleteUserServiceImpl 클래스의")
class DeleteUserServiceTest {

    private static final List<ReservationStatus> ACTIVE_STATUSES = List.of(ReservationStatus.RESERVED,
            ReservationStatus.RUNNING);

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
        deleteUserService = new DeleteUserServiceImpl(userRepository,
                reservationRepository,
                userReservationCleanupSupport);
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

    private Reservation createExpiredReservation(final User user, final Machine machine) {
        return Reservation.builder().user(user).machine(machine).reservedAt(LocalDateTime.now().minusMinutes(10))
                .startTime(LocalDateTime.now().minusMinutes(5)).status(ReservationStatus.RESERVED).build();
    }

    @Nested
    @DisplayName("execute 메서드는")
    class Describe_execute {

        @Nested
        @DisplayName("활성 예약이 없는 사용자를 삭제할 때")
        class Context_without_active_reservations {

            @Test
            @DisplayName("사용자를 삭제해야 한다")
            void it_deletes_user() {
                // Given
                Long userId = 1L;
                User user = createUser();
                given(userRepository.findById(userId)).willReturn(Optional.of(user));
                given(reservationRepository.existsCurrentlyActiveByUser(user)).willReturn(false);

                // When
                deleteUserService.execute(userId);

                // Then
                then(userRepository).should(times(1)).findById(userId);
                then(reservationRepository).should(times(1)).existsCurrentlyActiveByUser(user);
                then(userRepository).should(times(1)).delete(user);
            }
        }

        @Nested
        @DisplayName("RESERVED 상태의 예약이 있는 사용자를 삭제하려 할 때")
        class Context_with_reserved_reservation {

            @Test
            @DisplayName("ExpectedException을 던져야 한다")
            void it_throws_expected_exception() {
                // Given
                Long userId = 1L;
                User user = createUser();
                given(userRepository.findById(userId)).willReturn(Optional.of(user));
                given(reservationRepository.existsCurrentlyActiveByUser(user)).willReturn(true);

                // When & Then
                assertThatThrownBy(() -> deleteUserService.execute(userId)).isInstanceOf(ExpectedException.class)
                        .hasMessage("활성 예약이 있는 사용자는 삭제할 수 없습니다")
                        .hasFieldOrPropertyWithValue("statusCode", HttpStatus.BAD_REQUEST);

                then(userRepository).should(times(1)).findById(userId);
                then(reservationRepository).should(times(1)).existsCurrentlyActiveByUser(user);
                then(reservationRepository).should(never()).findByUserAndStatusInForUpdate(any(User.class), anyList());
                then(userRepository).should(never()).delete(any(User.class));
            }
        }

        @Nested
        @DisplayName("RUNNING 상태의 예약이 있는 사용자를 삭제하려 할 때")
        class Context_with_running_reservation {

            @Test
            @DisplayName("ExpectedException을 던져야 한다")
            void it_throws_expected_exception() {
                // Given
                Long userId = 1L;
                User user = createUser();
                given(userRepository.findById(userId)).willReturn(Optional.of(user));
                given(reservationRepository.existsCurrentlyActiveByUser(user)).willReturn(true);

                // When & Then
                assertThatThrownBy(() -> deleteUserService.execute(userId)).isInstanceOf(ExpectedException.class)
                        .hasMessage("활성 예약이 있는 사용자는 삭제할 수 없습니다")
                        .hasFieldOrPropertyWithValue("statusCode", HttpStatus.BAD_REQUEST);

                then(userRepository).should(times(1)).findById(userId);
                then(reservationRepository).should(times(1)).existsCurrentlyActiveByUser(user);
                then(reservationRepository).should(never()).findByUserAndStatusInForUpdate(any(User.class), anyList());
                then(userRepository).should(never()).delete(any(User.class));
            }
        }

        @Nested
        @DisplayName("COMPLETED 상태의 예약만 있는 사용자를 삭제할 때")
        class Context_with_completed_reservations_only {

            @Test
            @DisplayName("사용자를 삭제해야 한다")
            void it_deletes_user() {
                // Given
                Long userId = 1L;
                User user = createUser();
                given(userRepository.findById(userId)).willReturn(Optional.of(user));
                given(reservationRepository.existsCurrentlyActiveByUser(user)).willReturn(false);

                // When
                deleteUserService.execute(userId);

                // Then
                then(userRepository).should(times(1)).findById(userId);
                then(reservationRepository).should(times(1)).existsCurrentlyActiveByUser(user);
                then(userRepository).should(times(1)).delete(user);
            }
        }

        @Nested
        @DisplayName("CANCELLED 상태의 예약만 있는 사용자를 삭제할 때")
        class Context_with_cancelled_reservations_only {

            @Test
            @DisplayName("사용자를 삭제해야 한다")
            void it_deletes_user() {
                // Given
                Long userId = 1L;
                User user = createUser();
                given(userRepository.findById(userId)).willReturn(Optional.of(user));
                given(reservationRepository.existsCurrentlyActiveByUser(user)).willReturn(false);

                // When
                deleteUserService.execute(userId);

                // Then
                then(userRepository).should(times(1)).findById(userId);
                then(reservationRepository).should(times(1)).existsCurrentlyActiveByUser(user);
                then(userRepository).should(times(1)).delete(user);
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

                given(userRepository.findById(userId)).willReturn(Optional.of(user));
                given(reservationRepository.existsCurrentlyActiveByUser(user)).willReturn(false);
                given(reservationRepository.findByUserAndStatusInForUpdate(user, ACTIVE_STATUSES))
                        .willReturn(List.of(expired));

                // When
                deleteUserService.execute(userId);

                // Then
                assertThat(expired.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
                assertThat(machine.getAvailability()).isEqualTo(MachineAvailability.AVAILABLE);
                then(machineRepository).should(times(1)).saveAll(anyList());
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

                given(userRepository.findById(userId)).willReturn(Optional.of(user));
                given(reservationRepository.existsCurrentlyActiveByUser(user)).willReturn(false);
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

                given(userRepository.findById(userId)).willReturn(Optional.empty());

                // When & Then
                assertThatThrownBy(() -> deleteUserService.execute(userId)).isInstanceOf(ExpectedException.class)
                        .hasMessage("사용자를 찾을 수 없습니다").hasFieldOrPropertyWithValue("statusCode", HttpStatus.NOT_FOUND);

                then(userRepository).should(times(1)).findById(userId);
                then(reservationRepository).should(never()).existsCurrentlyActiveByUser(any(User.class));
                then(reservationRepository).should(never()).findByUserAndStatusInForUpdate(any(User.class), anyList());
                then(userRepository).should(never()).delete(any(User.class));
            }
        }
    }
}
