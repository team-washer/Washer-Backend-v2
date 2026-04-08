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
import team.washer.server.v2.domain.reservation.entity.Reservation;
import team.washer.server.v2.domain.reservation.enums.ReservationStatus;
import team.washer.server.v2.domain.reservation.repository.ReservationRepository;
import team.washer.server.v2.domain.reservation.service.impl.QueryReservationServiceImpl;
import team.washer.server.v2.domain.user.entity.User;

@ExtendWith(MockitoExtension.class)
@DisplayName("QueryReservationServiceImpl 클래스의")
class QueryReservationServiceTest {

    @InjectMocks
    private QueryReservationServiceImpl queryReservationService;

    @Mock
    private ReservationRepository reservationRepository;

    private User createUser() {
        return User.builder().name("김철수").studentId("20210001").roomNumber("301").grade(3).floor(3).build();
    }

    private Machine createMachine() {
        return Machine.builder().name("W-2F-L1").type(MachineType.WASHER).deviceId("device-1").floor(2)
                .position(Position.LEFT).number(1).status(MachineStatus.NORMAL)
                .availability(MachineAvailability.AVAILABLE).build();
    }

    private Reservation createReservation(final User user, final Machine machine) {
        return Reservation.builder().user(user).machine(machine).reservedAt(LocalDateTime.now())
                .status(ReservationStatus.RESERVED).build();
    }

    @Nested
    @DisplayName("execute 메서드는")
    class Describe_execute {

        @Nested
        @DisplayName("존재하는 예약 ID로 조회할 때")
        class Context_with_existing_reservation {

            @Test
            @DisplayName("예약 정보를 반환해야 한다")
            void it_returns_reservation_info() {
                // Given
                var reservationId = 1L;
                var user = createUser();
                var machine = createMachine();
                var reservation = createReservation(user, machine);

                given(reservationRepository.findById(reservationId)).willReturn(Optional.of(reservation));

                // When
                var result = queryReservationService.execute(reservationId);

                // Then
                assertThat(result).isNotNull();
                assertThat(result.userId()).isEqualTo(user.getId());
                assertThat(result.userName()).isEqualTo("김철수");
                assertThat(result.userRoomNumber()).isEqualTo("301");
                assertThat(result.machineId()).isEqualTo(machine.getId());
                assertThat(result.machineName()).isEqualTo("W-2F-L1");
                assertThat(result.status()).isEqualTo(ReservationStatus.RESERVED);
            }
        }

        @Nested
        @DisplayName("존재하지 않는 예약 ID로 조회할 때")
        class Context_with_nonexistent_reservation {

            @Test
            @DisplayName("ExpectedException이 발생하고 NOT_FOUND 상태를 반환해야 한다")
            void it_throws_not_found_exception() {
                // Given
                var reservationId = 999L;
                given(reservationRepository.findById(reservationId)).willReturn(Optional.empty());

                // When & Then
                assertThatThrownBy(() -> queryReservationService.execute(reservationId))
                        .isInstanceOf(ExpectedException.class).hasMessage("예약을 찾을 수 없습니다")
                        .satisfies(e -> assertThat(((ExpectedException) e).getStatusCode())
                                .isEqualTo(HttpStatus.NOT_FOUND));
            }
        }
    }
}
