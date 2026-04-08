package team.washer.server.v2.domain.reservation.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

import java.time.LocalDateTime;
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
import team.washer.server.v2.domain.machine.entity.Machine;
import team.washer.server.v2.domain.machine.enums.MachineAvailability;
import team.washer.server.v2.domain.machine.enums.MachineStatus;
import team.washer.server.v2.domain.machine.enums.MachineType;
import team.washer.server.v2.domain.machine.enums.Position;
import team.washer.server.v2.domain.machine.repository.MachineRepository;
import team.washer.server.v2.domain.reservation.entity.Reservation;
import team.washer.server.v2.domain.reservation.enums.ReservationStatus;
import team.washer.server.v2.domain.reservation.repository.ReservationRepository;
import team.washer.server.v2.domain.reservation.service.impl.CancelReservationServiceImpl;
import team.washer.server.v2.domain.reservation.util.PenaltyRedisUtil;
import team.washer.server.v2.domain.user.entity.User;
import team.washer.server.v2.global.security.provider.CurrentUserProvider;

@ExtendWith(MockitoExtension.class)
@DisplayName("CancelReservationServiceImpl 클래스의")
class CancelReservationServiceTest {

    @InjectMocks
    private CancelReservationServiceImpl cancelReservationService;

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private MachineRepository machineRepository;

    @Mock
    private PenaltyRedisUtil penaltyRedisUtil;

    @Mock
    private CurrentUserProvider currentUserProvider;

    @Mock
    private User user;

    private Machine createMachine() {
        return Machine.builder().name("W-2F-L1").type(MachineType.WASHER).deviceId("device-1").floor(2)
                .position(Position.LEFT).number(1).status(MachineStatus.NORMAL)
                .availability(MachineAvailability.RESERVED).build();
    }

    private Reservation createReservation(final ReservationStatus status, final Long userId) {
        var machine = createMachine();
        given(user.getId()).willReturn(userId);
        given(user.getRoomNumber()).willReturn("301");
        return Reservation.builder().user(user).machine(machine).reservedAt(LocalDateTime.now()).status(status).build();
    }

    @Nested
    @DisplayName("execute 메서드는")
    class Describe_execute {

        @Nested
        @DisplayName("RESERVED 상태의 예약을 소유자가 취소할 때")
        class Context_with_reserved_reservation_by_owner {

            @Test
            @DisplayName("패널티가 적용되고 예약이 취소되어야 한다")
            void it_cancels_with_penalty() {
                // Given
                var userId = 1L;
                var reservationId = 10L;
                var reservation = createReservation(ReservationStatus.RESERVED, userId);

                given(currentUserProvider.getCurrentUserId()).willReturn(userId);
                given(reservationRepository.findById(reservationId)).willReturn(Optional.of(reservation));
                given(penaltyRedisUtil.getCancellationCount(userId)).willReturn(0);

                // When
                var result = cancelReservationService.execute(reservationId);

                // Then
                assertThat(result).isNotNull();
                assertThat(result.success()).isTrue();
                assertThat(result.penaltyApplied()).isTrue();
                assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
                assertThat(reservation.getMachine().getAvailability()).isEqualTo(MachineAvailability.AVAILABLE);
                then(penaltyRedisUtil).should(times(1)).applyCooldown(userId);
                then(penaltyRedisUtil).should(times(1)).recordCancellation(userId);
                then(reservationRepository).should(times(1)).save(reservation);
                then(machineRepository).should(times(1)).save(reservation.getMachine());
            }
        }

        @Nested
        @DisplayName("RUNNING 상태의 예약을 소유자가 취소할 때")
        class Context_with_running_reservation_by_owner {

            @Test
            @DisplayName("패널티 없이 예약이 취소되어야 한다")
            void it_cancels_without_penalty() {
                // Given
                var userId = 1L;
                var reservationId = 10L;
                var reservation = createReservation(ReservationStatus.RUNNING, userId);

                given(currentUserProvider.getCurrentUserId()).willReturn(userId);
                given(reservationRepository.findById(reservationId)).willReturn(Optional.of(reservation));

                // When
                var result = cancelReservationService.execute(reservationId);

                // Then
                assertThat(result).isNotNull();
                assertThat(result.success()).isTrue();
                assertThat(result.penaltyApplied()).isFalse();
                assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
                then(penaltyRedisUtil).should(never()).applyCooldown(anyLong());
            }
        }

        @Nested
        @DisplayName("다른 사용자의 예약을 취소하려 할 때")
        class Context_with_different_user {

            @Test
            @DisplayName("ExpectedException이 발생하고 FORBIDDEN 상태를 반환해야 한다")
            void it_throws_forbidden_exception() {
                // Given
                var requestUserId = 2L;
                var ownerUserId = 1L;
                var reservationId = 10L;
                var reservation = createReservation(ReservationStatus.RESERVED, ownerUserId);

                given(currentUserProvider.getCurrentUserId()).willReturn(requestUserId);
                given(reservationRepository.findById(reservationId)).willReturn(Optional.of(reservation));

                // When & Then
                assertThatThrownBy(() -> cancelReservationService.execute(reservationId))
                        .isInstanceOf(ExpectedException.class).hasMessage("예약을 취소할 권한이 없습니다")
                        .satisfies(e -> assertThat(((ExpectedException) e).getStatusCode())
                                .isEqualTo(HttpStatus.FORBIDDEN));
            }
        }

        @Nested
        @DisplayName("이미 완료된 예약을 취소하려 할 때")
        class Context_with_completed_reservation {

            @Test
            @DisplayName("ExpectedException이 발생하고 BAD_REQUEST 상태를 반환해야 한다")
            void it_throws_bad_request_exception() {
                // Given
                var userId = 1L;
                var reservationId = 10L;
                var reservation = createReservation(ReservationStatus.COMPLETED, userId);

                given(currentUserProvider.getCurrentUserId()).willReturn(userId);
                given(reservationRepository.findById(reservationId)).willReturn(Optional.of(reservation));

                // When & Then
                assertThatThrownBy(() -> cancelReservationService.execute(reservationId))
                        .isInstanceOf(ExpectedException.class).hasMessage("활성 예약만 취소할 수 있습니다")
                        .satisfies(e -> assertThat(((ExpectedException) e).getStatusCode())
                                .isEqualTo(HttpStatus.BAD_REQUEST));
            }
        }

        @Nested
        @DisplayName("존재하지 않는 예약을 취소하려 할 때")
        class Context_with_nonexistent_reservation {

            @Test
            @DisplayName("ExpectedException이 발생하고 NOT_FOUND 상태를 반환해야 한다")
            void it_throws_not_found_exception() {
                // Given
                var userId = 1L;
                var reservationId = 999L;
                given(currentUserProvider.getCurrentUserId()).willReturn(userId);
                given(reservationRepository.findById(reservationId)).willReturn(Optional.empty());

                // When & Then
                assertThatThrownBy(() -> cancelReservationService.execute(reservationId))
                        .isInstanceOf(ExpectedException.class).hasMessage("예약을 찾을 수 없습니다")
                        .satisfies(e -> assertThat(((ExpectedException) e).getStatusCode())
                                .isEqualTo(HttpStatus.NOT_FOUND));
            }
        }
    }
}
