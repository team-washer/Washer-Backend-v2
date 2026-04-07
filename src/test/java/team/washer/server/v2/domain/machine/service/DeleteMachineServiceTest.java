package team.washer.server.v2.domain.machine.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

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
import team.washer.server.v2.domain.machine.service.impl.DeleteMachineServiceImpl;
import team.washer.server.v2.domain.reservation.entity.Reservation;
import team.washer.server.v2.domain.reservation.repository.ReservationRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName("DeleteMachineServiceImpl 클래스의")
class DeleteMachineServiceTest {

    @InjectMocks
    private DeleteMachineServiceImpl deleteMachineService;

    @Mock
    private MachineRepository machineRepository;

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private Reservation activeReservation;

    private Machine createMachine() {
        return Machine.builder().name("W-2F-L1").type(MachineType.WASHER).deviceId("device-1").floor(2)
                .position(Position.LEFT).number(1).status(MachineStatus.NORMAL)
                .availability(MachineAvailability.AVAILABLE).build();
    }

    @Nested
    @DisplayName("execute 메서드는")
    class Describe_execute {

        @Nested
        @DisplayName("활성 예약이 없는 기기를 삭제할 때")
        class Context_with_no_active_reservation {

            @Test
            @DisplayName("기기를 삭제하고 삭제된 기기 정보를 반환해야 한다")
            void it_deletes_machine_and_returns_info() {
                // Given
                var machineId = 1L;
                var machine = createMachine();
                given(machineRepository.findById(machineId)).willReturn(Optional.of(machine));
                given(reservationRepository.findActiveReservationByMachineId(machineId)).willReturn(Optional.empty());

                // When
                var result = deleteMachineService.execute(machineId);

                // Then
                assertThat(result).isNotNull();
                assertThat(result.name()).isEqualTo("W-2F-L1");
                then(machineRepository).should(times(1)).delete(machine);
            }
        }

        @Nested
        @DisplayName("활성 예약이 있는 기기를 삭제하려 할 때")
        class Context_with_active_reservation {

            @Test
            @DisplayName("ExpectedException이 발생하고 BAD_REQUEST 상태를 반환해야 한다")
            void it_throws_bad_request_exception() {
                // Given
                var machineId = 1L;
                var machine = createMachine();
                given(machineRepository.findById(machineId)).willReturn(Optional.of(machine));
                given(reservationRepository.findActiveReservationByMachineId(machineId))
                        .willReturn(Optional.of(activeReservation));

                // When & Then
                assertThatThrownBy(() -> deleteMachineService.execute(machineId))
                        .isInstanceOf(ExpectedException.class)
                        .hasMessage("활성 예약이 존재하는 기기는 삭제할 수 없습니다")
                        .satisfies(e -> assertThat(((ExpectedException) e).getStatusCode())
                                .isEqualTo(HttpStatus.BAD_REQUEST));

                then(machineRepository).should(never()).delete(any(Machine.class));
            }
        }

        @Nested
        @DisplayName("존재하지 않는 기기를 삭제하려 할 때")
        class Context_with_nonexistent_machine {

            @Test
            @DisplayName("ExpectedException이 발생하고 NOT_FOUND 상태를 반환해야 한다")
            void it_throws_not_found_exception() {
                // Given
                var machineId = 999L;
                given(machineRepository.findById(machineId)).willReturn(Optional.empty());

                // When & Then
                assertThatThrownBy(() -> deleteMachineService.execute(machineId))
                        .isInstanceOf(ExpectedException.class)
                        .hasMessage("기기를 찾을 수 없습니다")
                        .satisfies(e -> assertThat(((ExpectedException) e).getStatusCode())
                                .isEqualTo(HttpStatus.NOT_FOUND));
            }
        }
    }
}
