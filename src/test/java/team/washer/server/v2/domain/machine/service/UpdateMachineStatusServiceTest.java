package team.washer.server.v2.domain.machine.service;

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

import team.washer.server.v2.domain.machine.dto.response.MachineStatusUpdateResDto;
import team.washer.server.v2.domain.machine.entity.Machine;
import team.washer.server.v2.domain.machine.enums.MachineAvailability;
import team.washer.server.v2.domain.machine.enums.MachineStatus;
import team.washer.server.v2.domain.machine.enums.MachineType;
import team.washer.server.v2.domain.machine.enums.Position;
import team.washer.server.v2.domain.machine.repository.MachineRepository;
import team.washer.server.v2.domain.machine.service.impl.UpdateMachineStatusServiceImpl;
import team.washer.server.v2.domain.reservation.entity.Reservation;
import team.washer.server.v2.domain.reservation.enums.ReservationStatus;
import team.washer.server.v2.domain.reservation.repository.ReservationRepository;
import team.washer.server.v2.domain.user.entity.User;
import team.washer.server.v2.global.common.error.exception.ExpectedException;

@ExtendWith(MockitoExtension.class)
@DisplayName("UpdateMachineStatusServiceImpl 클래스의")
class UpdateMachineStatusServiceTest {

    @InjectMocks
    private UpdateMachineStatusServiceImpl updateMachineStatusService;

    @Mock
    private MachineRepository machineRepository;

    @Mock
    private ReservationRepository reservationRepository;

    private Machine createMachine(MachineStatus status, MachineAvailability availability) {
        return Machine.builder().name("W-2F-L1").type(MachineType.WASHER).deviceId("device-1").floor(2)
                .position(Position.LEFT).number(1).status(status).availability(availability).build();
    }

    private Reservation createReservation(Machine machine, ReservationStatus status) {
        User user = User.builder().name("김철수").studentId("20210001").roomNumber("301").grade(3).floor(3).penaltyCount(0)
                .build();

        return Reservation.builder().user(user).machine(machine).reservedAt(LocalDateTime.now())
                .startTime(LocalDateTime.now().plusMinutes(10)).status(status).build();
    }

    @Nested
    @DisplayName("execute 메서드는")
    class Describe_execute {

        @Nested
        @DisplayName("기기를 MALFUNCTION 상태로 변경할 때")
        class Context_with_malfunction_status {

            @Test
            @DisplayName("기기 상태를 MALFUNCTION, 가용성을 UNAVAILABLE로 변경해야 한다")
            void it_changes_machine_to_malfunction() {
                // Given
                Long machineId = 1L;
                Machine machine = createMachine(MachineStatus.NORMAL, MachineAvailability.AVAILABLE);

                given(machineRepository.findById(machineId)).willReturn(Optional.of(machine));
                given(machineRepository.save(any(Machine.class))).willAnswer(invocation -> invocation.getArgument(0));

                // When
                MachineStatusUpdateResDto result = updateMachineStatusService.execute(machineId,
                        MachineStatus.MALFUNCTION);

                // Then
                assertThat(result.status()).isEqualTo(MachineStatus.MALFUNCTION);
                assertThat(result.availability()).isEqualTo(MachineAvailability.UNAVAILABLE);
                then(machineRepository).should(times(1)).findById(machineId);
                then(machineRepository).should(times(1)).save(any(Machine.class));
            }
        }

        @Nested
        @DisplayName("기기를 NORMAL 상태로 변경할 때")
        class Context_with_normal_status {

            @Nested
            @DisplayName("활성 예약이 없는 경우")
            class Context_without_active_reservation {

                @Test
                @DisplayName("기기 상태를 NORMAL, 가용성을 AVAILABLE로 변경해야 한다")
                void it_changes_machine_to_normal_and_available() {
                    // Given
                    Long machineId = 1L;
                    Machine machine = createMachine(MachineStatus.MALFUNCTION, MachineAvailability.UNAVAILABLE);

                    given(machineRepository.findById(machineId)).willReturn(Optional.of(machine));
                    given(reservationRepository.findByMachineAndStatusIn(any(Machine.class), anyList()))
                            .willReturn(List.of());
                    given(machineRepository.save(any(Machine.class)))
                            .willAnswer(invocation -> invocation.getArgument(0));

                    // When
                    MachineStatusUpdateResDto result = updateMachineStatusService.execute(machineId,
                            MachineStatus.NORMAL);

                    // Then
                    assertThat(result.status()).isEqualTo(MachineStatus.NORMAL);
                    assertThat(result.availability()).isEqualTo(MachineAvailability.AVAILABLE);
                    then(machineRepository).should(times(1)).findById(machineId);
                    then(reservationRepository).should(times(1)).findByMachineAndStatusIn(any(Machine.class),
                            anyList());
                    then(machineRepository).should(times(1)).save(any(Machine.class));
                }
            }

            @Nested
            @DisplayName("활성 예약이 있는 경우")
            class Context_with_active_reservation {

                @Test
                @DisplayName("기기 상태를 NORMAL, 가용성을 RESERVED로 변경해야 한다")
                void it_changes_machine_to_normal_and_reserved() {
                    // Given
                    Long machineId = 1L;
                    Machine machine = createMachine(MachineStatus.MALFUNCTION, MachineAvailability.UNAVAILABLE);
                    Reservation reservation = createReservation(machine, ReservationStatus.RESERVED);

                    given(machineRepository.findById(machineId)).willReturn(Optional.of(machine));
                    given(reservationRepository.findByMachineAndStatusIn(any(Machine.class), anyList()))
                            .willReturn(List.of(reservation));
                    given(machineRepository.save(any(Machine.class)))
                            .willAnswer(invocation -> invocation.getArgument(0));

                    // When
                    MachineStatusUpdateResDto result = updateMachineStatusService.execute(machineId,
                            MachineStatus.NORMAL);

                    // Then
                    assertThat(result.status()).isEqualTo(MachineStatus.NORMAL);
                    assertThat(result.availability()).isEqualTo(MachineAvailability.RESERVED);
                    then(machineRepository).should(times(1)).findById(machineId);
                    then(reservationRepository).should(times(1)).findByMachineAndStatusIn(any(Machine.class),
                            anyList());
                    then(machineRepository).should(times(1)).save(any(Machine.class));
                }
            }
        }

        @Nested
        @DisplayName("존재하지 않는 기기 ID로 요청할 때")
        class Context_with_nonexistent_machine_id {

            @Test
            @DisplayName("ExpectedException을 던져야 한다")
            void it_throws_expected_exception() {
                // Given
                Long machineId = 999L;

                given(machineRepository.findById(machineId)).willReturn(Optional.empty());

                // When & Then
                assertThatThrownBy(() -> updateMachineStatusService.execute(machineId, MachineStatus.NORMAL))
                        .isInstanceOf(ExpectedException.class).hasMessage("기기를 찾을 수 없습니다")
                        .hasFieldOrPropertyWithValue("statusCode", HttpStatus.NOT_FOUND);

                then(machineRepository).should(times(1)).findById(machineId);
                then(machineRepository).should(never()).save(any(Machine.class));
            }
        }
    }
}
