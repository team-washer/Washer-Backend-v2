package team.washer.server.v2.domain.user.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import team.themoment.sdk.exception.ExpectedException;
import team.washer.server.v2.domain.auth.repository.redis.RefreshTokenRedisRepository;
import team.washer.server.v2.domain.auth.util.WithdrawnStudentRedisUtil;
import team.washer.server.v2.domain.machine.entity.Machine;
import team.washer.server.v2.domain.machine.enums.MachineAvailability;
import team.washer.server.v2.domain.machine.enums.MachineStatus;
import team.washer.server.v2.domain.machine.enums.MachineType;
import team.washer.server.v2.domain.machine.enums.Position;
import team.washer.server.v2.domain.machine.repository.MachineRepository;
import team.washer.server.v2.domain.reservation.entity.Reservation;
import team.washer.server.v2.domain.reservation.enums.ReservationStatus;
import team.washer.server.v2.domain.reservation.repository.ReservationRepository;
import team.washer.server.v2.domain.user.entity.User;
import team.washer.server.v2.domain.user.repository.UserRepository;
import team.washer.server.v2.domain.user.service.impl.WithdrawUserServiceImpl;
import team.washer.server.v2.global.security.provider.CurrentUserProvider;

@ExtendWith(MockitoExtension.class)
@DisplayName("WithdrawUserServiceImpl 클래스의")
class WithdrawUserServiceTest {

    @InjectMocks
    private WithdrawUserServiceImpl withdrawUserService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private MachineRepository machineRepository;

    @Mock
    private RefreshTokenRedisRepository refreshTokenRedisRepository;

    @Mock
    private WithdrawnStudentRedisUtil withdrawnStudentRedisUtil;

    @Mock
    private CurrentUserProvider currentUserProvider;

    private User createUser() {
        return User.builder().name("김철수").studentId("20210001").roomNumber("301").grade(3).floor(3).penaltyCount(0)
                .build();
    }

    private Machine createMachine() {
        return Machine.builder().name("W-2F-L1").type(MachineType.WASHER).deviceId("device-1").floor(2)
                .position(Position.LEFT).number(1).status(MachineStatus.NORMAL)
                .availability(MachineAvailability.RESERVED).build();
    }

    private Reservation createReservation(final ReservationStatus status, final User user, final Machine machine) {
        return Reservation.builder().user(user).machine(machine).reservedAt(LocalDateTime.now())
                .startTime(LocalDateTime.now().plusMinutes(10)).status(status).build();
    }

    @Nested
    @DisplayName("execute 메서드는")
    class Describe_execute {

        @Nested
        @DisplayName("활성 예약이 없는 사용자가 탈퇴할 때")
        class Context_without_active_reservations {

            @Test
            @DisplayName("리프레시 토큰을 삭제하고 탈퇴 학번을 기록한 후 사용자를 삭제해야 한다")
            void it_deletes_user_with_token_and_withdrawal_recorded() {
                // Given
                Long userId = 1L;
                User user = createUser();
                List<ReservationStatus> activeStatuses = List.of(ReservationStatus.RESERVED, ReservationStatus.RUNNING);

                given(currentUserProvider.getCurrentUserId()).willReturn(userId);
                given(userRepository.findById(userId)).willReturn(Optional.of(user));
                given(reservationRepository.findByUserAndStatusIn(user, activeStatuses)).willReturn(List.of());

                // When
                withdrawUserService.execute();

                // Then
                then(reservationRepository).should(times(1)).findByUserAndStatusIn(user, activeStatuses);
                then(machineRepository).should(never()).save(any(Machine.class));
                then(refreshTokenRedisRepository).should(times(1)).deleteById(userId);
                then(withdrawnStudentRedisUtil).should(times(1)).markWithdrawn(user.getStudentId());
                then(userRepository).should(times(1)).delete(user);
            }
        }

        @Nested
        @DisplayName("RESERVED 상태의 예약이 있는 사용자가 탈퇴할 때")
        class Context_with_reserved_reservation {

            @Test
            @DisplayName("예약을 패널티 없이 취소하고 기기를 AVAILABLE 상태로 변경한 후 사용자를 삭제해야 한다")
            void it_cancels_reservation_and_deletes_user() {
                // Given
                Long userId = 1L;
                User user = createUser();
                Machine machine = createMachine();
                Reservation reservation = createReservation(ReservationStatus.RESERVED, user, machine);
                List<ReservationStatus> activeStatuses = List.of(ReservationStatus.RESERVED, ReservationStatus.RUNNING);

                given(currentUserProvider.getCurrentUserId()).willReturn(userId);
                given(userRepository.findById(userId)).willReturn(Optional.of(user));
                given(reservationRepository.findByUserAndStatusIn(user, activeStatuses))
                        .willReturn(List.of(reservation));

                // When
                withdrawUserService.execute();

                // Then
                assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
                assertThat(machine.getAvailability()).isEqualTo(MachineAvailability.AVAILABLE);
                then(machineRepository).should(times(1)).saveAll(anyList());
                then(refreshTokenRedisRepository).should(times(1)).deleteById(userId);
                then(withdrawnStudentRedisUtil).should(times(1)).markWithdrawn(user.getStudentId());
                then(userRepository).should(times(1)).delete(user);
            }
        }

        @Nested
        @DisplayName("RUNNING 상태의 예약이 있는 사용자가 탈퇴할 때")
        class Context_with_running_reservation {

            @Test
            @DisplayName("예약을 패널티 없이 취소하고 기기를 AVAILABLE 상태로 변경한 후 사용자를 삭제해야 한다")
            void it_cancels_reservation_and_deletes_user() {
                // Given
                Long userId = 1L;
                User user = createUser();
                Machine machine = createMachine();
                Reservation reservation = createReservation(ReservationStatus.RUNNING, user, machine);
                List<ReservationStatus> activeStatuses = List.of(ReservationStatus.RESERVED, ReservationStatus.RUNNING);

                given(currentUserProvider.getCurrentUserId()).willReturn(userId);
                given(userRepository.findById(userId)).willReturn(Optional.of(user));
                given(reservationRepository.findByUserAndStatusIn(user, activeStatuses))
                        .willReturn(List.of(reservation));

                // When
                withdrawUserService.execute();

                // Then
                assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
                assertThat(machine.getAvailability()).isEqualTo(MachineAvailability.AVAILABLE);
                then(machineRepository).should(times(1)).saveAll(anyList());
                then(refreshTokenRedisRepository).should(times(1)).deleteById(userId);
                then(withdrawnStudentRedisUtil).should(times(1)).markWithdrawn(user.getStudentId());
                then(userRepository).should(times(1)).delete(user);
            }
        }

        @Nested
        @DisplayName("RESERVED와 RUNNING 예약이 동시에 있는 사용자가 탈퇴할 때")
        class Context_with_multiple_active_reservations {

            @Test
            @DisplayName("모든 활성 예약을 취소하고 사용자를 삭제해야 한다")
            void it_cancels_all_reservations_and_deletes_user() {
                // Given
                Long userId = 1L;
                User user = createUser();
                Machine machine1 = createMachine();
                Machine machine2 = Machine.builder().name("W-2F-R1").type(MachineType.WASHER).deviceId("device-2")
                        .floor(2).position(Position.RIGHT).number(2).status(MachineStatus.NORMAL)
                        .availability(MachineAvailability.IN_USE).build();
                Reservation reserved = createReservation(ReservationStatus.RESERVED, user, machine1);
                Reservation running = createReservation(ReservationStatus.RUNNING, user, machine2);
                List<ReservationStatus> activeStatuses = List.of(ReservationStatus.RESERVED, ReservationStatus.RUNNING);

                given(currentUserProvider.getCurrentUserId()).willReturn(userId);
                given(userRepository.findById(userId)).willReturn(Optional.of(user));
                given(reservationRepository.findByUserAndStatusIn(user, activeStatuses))
                        .willReturn(List.of(reserved, running));

                // When
                withdrawUserService.execute();

                // Then
                assertThat(reserved.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
                assertThat(machine1.getAvailability()).isEqualTo(MachineAvailability.AVAILABLE);
                assertThat(running.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
                assertThat(machine2.getAvailability()).isEqualTo(MachineAvailability.AVAILABLE);
                then(machineRepository).should(times(1)).saveAll(anyList());
                then(refreshTokenRedisRepository).should(times(1)).deleteById(userId);
                then(withdrawnStudentRedisUtil).should(times(1)).markWithdrawn(user.getStudentId());
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

                given(currentUserProvider.getCurrentUserId()).willReturn(userId);
                given(userRepository.findById(userId)).willReturn(Optional.empty());

                // When & Then
                assertThatThrownBy(() -> withdrawUserService.execute()).isInstanceOf(ExpectedException.class)
                        .hasMessage("사용자를 찾을 수 없습니다").hasFieldOrPropertyWithValue("statusCode", HttpStatus.NOT_FOUND);

                then(reservationRepository).should(never()).findByUserAndStatusIn(any(User.class), anyList());
                then(refreshTokenRedisRepository).should(never()).deleteById(anyLong());
                then(withdrawnStudentRedisUtil).should(never()).markWithdrawn(anyString());
                then(userRepository).should(never()).delete(any(User.class));
            }
        }
    }
}
