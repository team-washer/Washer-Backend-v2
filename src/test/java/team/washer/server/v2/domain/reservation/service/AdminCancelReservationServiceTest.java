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

import team.washer.server.v2.domain.machine.entity.Machine;
import team.washer.server.v2.domain.machine.enums.MachineAvailability;
import team.washer.server.v2.domain.machine.enums.MachineStatus;
import team.washer.server.v2.domain.machine.enums.MachineType;
import team.washer.server.v2.domain.machine.enums.Position;
import team.washer.server.v2.domain.reservation.dto.response.AdminCancellationResDto;
import team.washer.server.v2.domain.reservation.entity.Reservation;
import team.washer.server.v2.domain.reservation.enums.ReservationStatus;
import team.washer.server.v2.domain.reservation.repository.ReservationRepository;
import team.washer.server.v2.domain.reservation.service.impl.AdminCancelReservationServiceImpl;
import team.washer.server.v2.domain.user.entity.User;
import team.washer.server.v2.global.common.error.exception.ExpectedException;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminCancelReservationServiceImpl 클래스의")
class AdminCancelReservationServiceTest {

    @InjectMocks
    private AdminCancelReservationServiceImpl adminCancelReservationService;

    @Mock
    private ReservationRepository reservationRepository;

    private User createUser() {
        return User.builder().name("김철수").studentId("20210001").roomNumber("301").grade(3).floor(3).penaltyCount(0)
                .build();
    }

    private Machine createMachine() {
        return Machine.builder().name("W-2F-L1").type(MachineType.WASHER).deviceId("device-1").floor(2)
                .position(Position.LEFT).number(1).status(MachineStatus.NORMAL)
                .availability(MachineAvailability.RESERVED).build();
    }

    private Reservation createReservation(Long id, ReservationStatus status) {
        User user = createUser();
        Machine machine = createMachine();

        Reservation reservation = Reservation.builder().user(user).machine(machine).reservedAt(LocalDateTime.now())
                .startTime(LocalDateTime.now().plusMinutes(10)).status(status).build();

        // ID 설정 (reflection 사용)
        try {
            var idField = reservation.getClass().getSuperclass().getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(reservation, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return reservation;
    }

    @Nested
    @DisplayName("execute 메서드는")
    class Describe_execute {

        @Nested
        @DisplayName("RESERVED 상태의 예약을 취소할 때")
        class Context_with_reserved_status {

            @Test
            @DisplayName("예약을 취소하고 기기를 AVAILABLE 상태로 변경해야 한다")
            void it_cancels_reservation_and_makes_machine_available() {
                // Given
                Long reservationId = 1L;
                Reservation reservation = createReservation(reservationId, ReservationStatus.RESERVED);

                given(reservationRepository.findById(reservationId)).willReturn(Optional.of(reservation));
                given(reservationRepository.save(any(Reservation.class)))
                        .willAnswer(invocation -> invocation.getArgument(0));

                // When
                AdminCancellationResDto result = adminCancelReservationService.execute(reservationId);

                // Then
                assertThat(result.reservationId()).isEqualTo(reservationId);
                assertThat(result.userName()).isEqualTo("김철수");
                assertThat(result.machineName()).isEqualTo("W-2F-L1");
                assertThat(result.penaltyApplied()).isFalse();
                assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
                assertThat(reservation.getMachine().getAvailability()).isEqualTo(MachineAvailability.AVAILABLE);
                then(reservationRepository).should(times(1)).findById(reservationId);
                then(reservationRepository).should(times(1)).save(any(Reservation.class));
            }
        }

        @Nested
        @DisplayName("CONFIRMED 상태의 예약을 취소할 때")
        class Context_with_confirmed_status {

            @Test
            @DisplayName("예약을 취소하고 기기를 AVAILABLE 상태로 변경해야 한다")
            void it_cancels_reservation_and_makes_machine_available() {
                // Given
                Long reservationId = 1L;
                Reservation reservation = createReservation(reservationId, ReservationStatus.CONFIRMED);

                given(reservationRepository.findById(reservationId)).willReturn(Optional.of(reservation));
                given(reservationRepository.save(any(Reservation.class)))
                        .willAnswer(invocation -> invocation.getArgument(0));

                // When
                AdminCancellationResDto result = adminCancelReservationService.execute(reservationId);

                // Then
                assertThat(result.reservationId()).isEqualTo(reservationId);
                assertThat(result.penaltyApplied()).isFalse();
                assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
                assertThat(reservation.getMachine().getAvailability()).isEqualTo(MachineAvailability.AVAILABLE);
                then(reservationRepository).should(times(1)).findById(reservationId);
                then(reservationRepository).should(times(1)).save(any(Reservation.class));
            }
        }

        @Nested
        @DisplayName("RUNNING 상태의 예약을 취소할 때")
        class Context_with_running_status {

            @Test
            @DisplayName("예약을 취소하고 기기를 AVAILABLE 상태로 변경해야 한다")
            void it_cancels_reservation_and_makes_machine_available() {
                // Given
                Long reservationId = 1L;
                Reservation reservation = createReservation(reservationId, ReservationStatus.RUNNING);

                given(reservationRepository.findById(reservationId)).willReturn(Optional.of(reservation));
                given(reservationRepository.save(any(Reservation.class)))
                        .willAnswer(invocation -> invocation.getArgument(0));

                // When
                AdminCancellationResDto result = adminCancelReservationService.execute(reservationId);

                // Then
                assertThat(result.reservationId()).isEqualTo(reservationId);
                assertThat(result.penaltyApplied()).isFalse();
                assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
                assertThat(reservation.getMachine().getAvailability()).isEqualTo(MachineAvailability.AVAILABLE);
                then(reservationRepository).should(times(1)).findById(reservationId);
                then(reservationRepository).should(times(1)).save(any(Reservation.class));
            }
        }

        @Nested
        @DisplayName("COMPLETED 상태의 예약을 취소하려 할 때")
        class Context_with_completed_status {

            @Test
            @DisplayName("ExpectedException을 던져야 한다")
            void it_throws_expected_exception() {
                // Given
                Long reservationId = 1L;
                Reservation reservation = createReservation(reservationId, ReservationStatus.COMPLETED);

                given(reservationRepository.findById(reservationId)).willReturn(Optional.of(reservation));

                // When & Then
                assertThatThrownBy(() -> adminCancelReservationService.execute(reservationId))
                        .isInstanceOf(ExpectedException.class).hasMessage("이미 완료된 예약은 취소할 수 없습니다")
                        .hasFieldOrPropertyWithValue("statusCode", HttpStatus.BAD_REQUEST);

                then(reservationRepository).should(times(1)).findById(reservationId);
                then(reservationRepository).should(never()).save(any(Reservation.class));
            }
        }

        @Nested
        @DisplayName("CANCELLED 상태의 예약을 취소하려 할 때")
        class Context_with_cancelled_status {

            @Test
            @DisplayName("ExpectedException을 던져야 한다")
            void it_throws_expected_exception() {
                // Given
                Long reservationId = 1L;
                Reservation reservation = createReservation(reservationId, ReservationStatus.CANCELLED);

                given(reservationRepository.findById(reservationId)).willReturn(Optional.of(reservation));

                // When & Then
                assertThatThrownBy(() -> adminCancelReservationService.execute(reservationId))
                        .isInstanceOf(ExpectedException.class).hasMessage("이미 취소된 예약입니다")
                        .hasFieldOrPropertyWithValue("statusCode", HttpStatus.BAD_REQUEST);

                then(reservationRepository).should(times(1)).findById(reservationId);
                then(reservationRepository).should(never()).save(any(Reservation.class));
            }
        }

        @Nested
        @DisplayName("존재하지 않는 예약 ID로 요청할 때")
        class Context_with_nonexistent_reservation_id {

            @Test
            @DisplayName("ExpectedException을 던져야 한다")
            void it_throws_expected_exception() {
                // Given
                Long reservationId = 999L;

                given(reservationRepository.findById(reservationId)).willReturn(Optional.empty());

                // When & Then
                assertThatThrownBy(() -> adminCancelReservationService.execute(reservationId))
                        .isInstanceOf(ExpectedException.class).hasMessage("예약을 찾을 수 없습니다")
                        .hasFieldOrPropertyWithValue("statusCode", HttpStatus.NOT_FOUND);

                then(reservationRepository).should(times(1)).findById(reservationId);
                then(reservationRepository).should(never()).save(any(Reservation.class));
            }
        }
    }
}
