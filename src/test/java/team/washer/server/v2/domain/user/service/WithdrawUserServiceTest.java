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

    private static final List<ReservationStatus> ACTIVE_STATUSES = List.of(ReservationStatus.RESERVED,
            ReservationStatus.RUNNING);

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
        if (status == ReservationStatus.RUNNING) {
            machine.markAsInUse();
        }
        return Reservation.builder().user(user).machine(machine).reservedAt(LocalDateTime.now())
                .startTime(LocalDateTime.now().plusMinutes(10)).status(status).build();
    }

    @Nested
    @DisplayName("execute 메서드는")
    class Describe_execute {

        @Nested
        @DisplayName("활성 예약이 없는 사용자가 탈퇴하면")
        class Context_without_active_reservations {

            @Test
            @DisplayName("리프레시 토큰을 제거하고 탈퇴 학번을 기록한 뒤 사용자를 삭제해야 한다")
            void it_deletes_user_with_token_and_withdrawal_recorded() {
                // Given
                Long userId = 1L;
                User user = createUser();

                given(currentUserProvider.getCurrentUserId()).willReturn(userId);
                given(userRepository.findById(userId)).willReturn(Optional.of(user));
                given(reservationRepository.findByUserAndStatusInForUpdate(user, ACTIVE_STATUSES))
                        .willReturn(List.of());

                // When
                withdrawUserService.execute();

                // Then
                then(reservationRepository).should(times(1)).findByUserAndStatusInForUpdate(user, ACTIVE_STATUSES);
                then(machineRepository).should(never()).save(any(Machine.class));
                then(refreshTokenRedisRepository).should(times(1)).deleteById(userId);
                then(withdrawnStudentRedisUtil).should(times(1)).markWithdrawn(user.getStudentId());
                then(userRepository).should(times(1)).delete(user);
            }
        }

        @Nested
        @DisplayName("RESERVED 상태의 예약이 있는 사용자가 탈퇴하면")
        class Context_with_reserved_reservation {

            @Test
            @DisplayName("예약을 취소하고 기기를 AVAILABLE 상태로 변경한 뒤 사용자를 삭제해야 한다")
            void it_cancels_reservation_and_deletes_user() {
                // Given
                Long userId = 1L;
                User user = createUser();
                Machine machine = createMachine();
                Reservation reservation = createReservation(ReservationStatus.RESERVED, user, machine);

                given(currentUserProvider.getCurrentUserId()).willReturn(userId);
                given(userRepository.findById(userId)).willReturn(Optional.of(user));
                given(reservationRepository.findByUserAndStatusInForUpdate(user, ACTIVE_STATUSES))
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
        @DisplayName("RUNNING 상태의 예약이 있는 사용자가 탈퇴하면")
        class Context_with_running_reservation {

            @Test
            @DisplayName("예약과 기기 상태를 보존하고 CONFLICT 예외를 발생시켜야 한다")
            void it_keeps_running_reservation_and_throws_conflict() {
                // Given
                Long userId = 1L;
                User user = createUser();
                Machine machine = createMachine();
                Reservation reservation = createReservation(ReservationStatus.RUNNING, user, machine);

                given(currentUserProvider.getCurrentUserId()).willReturn(userId);
                given(userRepository.findById(userId)).willReturn(Optional.of(user));
                given(reservationRepository.findByUserAndStatusInForUpdate(user, ACTIVE_STATUSES))
                        .willReturn(List.of(reservation));

                // When & Then
                assertThatThrownBy(() -> withdrawUserService.execute()).isInstanceOf(ExpectedException.class)
                        .hasMessage("기기 사용 중에는 회원탈퇴를 할 수 없습니다. 사용 완료 후 다시 시도해주세요.")
                        .hasFieldOrPropertyWithValue("statusCode", HttpStatus.CONFLICT);

                assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.RUNNING);
                assertThat(machine.getAvailability()).isEqualTo(MachineAvailability.IN_USE);
                then(machineRepository).should(never()).saveAll(anyList());
                then(refreshTokenRedisRepository).should(never()).deleteById(anyLong());
                then(withdrawnStudentRedisUtil).should(never()).markWithdrawn(anyString());
                then(userRepository).should(never()).delete(any(User.class));
            }
        }

        @Nested
        @DisplayName("RESERVED와 RUNNING 예약이 동시에 있는 사용자가 탈퇴하면")
        class Context_with_multiple_active_reservations {

            @Test
            @DisplayName("아무 예약도 변경하지 않고 CONFLICT 예외를 발생시켜야 한다")
            void it_keeps_all_reservations_and_throws_conflict() {
                // Given
                Long userId = 1L;
                User user = createUser();
                Machine machine1 = createMachine();
                Machine machine2 = Machine.builder().name("W-2F-R1").type(MachineType.WASHER).deviceId("device-2")
                        .floor(2).position(Position.RIGHT).number(2).status(MachineStatus.NORMAL)
                        .availability(MachineAvailability.IN_USE).build();
                Reservation reserved = createReservation(ReservationStatus.RESERVED, user, machine1);
                Reservation running = createReservation(ReservationStatus.RUNNING, user, machine2);

                given(currentUserProvider.getCurrentUserId()).willReturn(userId);
                given(userRepository.findById(userId)).willReturn(Optional.of(user));
                given(reservationRepository.findByUserAndStatusInForUpdate(user, ACTIVE_STATUSES))
                        .willReturn(List.of(reserved, running));

                // When & Then
                assertThatThrownBy(() -> withdrawUserService.execute()).isInstanceOf(ExpectedException.class)
                        .hasFieldOrPropertyWithValue("statusCode", HttpStatus.CONFLICT);

                assertThat(reserved.getStatus()).isEqualTo(ReservationStatus.RESERVED);
                assertThat(machine1.getAvailability()).isEqualTo(MachineAvailability.RESERVED);
                assertThat(running.getStatus()).isEqualTo(ReservationStatus.RUNNING);
                assertThat(machine2.getAvailability()).isEqualTo(MachineAvailability.IN_USE);
                then(machineRepository).should(never()).saveAll(anyList());
                then(refreshTokenRedisRepository).should(never()).deleteById(anyLong());
                then(withdrawnStudentRedisUtil).should(never()).markWithdrawn(anyString());
                then(userRepository).should(never()).delete(any(User.class));
            }
        }

        @Nested
        @DisplayName("존재하지 않는 사용자 ID로 요청하면")
        class Context_with_nonexistent_user_id {

            @Test
            @DisplayName("ExpectedException을 발생시켜야 한다")
            void it_throws_expected_exception() {
                // Given
                Long userId = 999L;

                given(currentUserProvider.getCurrentUserId()).willReturn(userId);
                given(userRepository.findById(userId)).willReturn(Optional.empty());

                // When & Then
                assertThatThrownBy(() -> withdrawUserService.execute()).isInstanceOf(ExpectedException.class)
                        .hasMessage("사용자를 찾을 수 없습니다").hasFieldOrPropertyWithValue("statusCode", HttpStatus.NOT_FOUND);

                then(reservationRepository).should(never()).findByUserAndStatusInForUpdate(any(User.class), anyList());
                then(refreshTokenRedisRepository).should(never()).deleteById(anyLong());
                then(withdrawnStudentRedisUtil).should(never()).markWithdrawn(anyString());
                then(userRepository).should(never()).delete(any(User.class));
            }
        }
    }
}
