package team.washer.server.v2.domain.smartthings.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import team.washer.server.v2.domain.machine.entity.Machine;
import team.washer.server.v2.domain.machine.enums.MachineAvailability;
import team.washer.server.v2.domain.machine.enums.MachineStatus;
import team.washer.server.v2.domain.machine.enums.MachineType;
import team.washer.server.v2.domain.machine.enums.Position;
import team.washer.server.v2.domain.machine.repository.MachineRepository;
import team.washer.server.v2.domain.reservation.enums.ReservationStatus;
import team.washer.server.v2.domain.reservation.repository.ReservationRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName("WasherTubCleanMachineGuard 클래스의")
class WasherTubCleanMachineGuardTest {

    private static final List<ReservationStatus> ACTIVE_STATUSES = List.of(ReservationStatus.RESERVED,
            ReservationStatus.RUNNING);

    @InjectMocks
    private WasherTubCleanMachineGuard machineGuard;

    @Mock
    private MachineRepository machineRepository;

    @Mock
    private ReservationRepository reservationRepository;

    private Machine createMachine() {
        var machine = Machine.builder().name("W-2F-L1").type(MachineType.WASHER).deviceId("device-1").floor(2)
                .position(Position.LEFT).number(1).status(MachineStatus.NORMAL)
                .availability(MachineAvailability.AVAILABLE).build();
        ReflectionTestUtils.setField(machine, "id", 1L);
        return machine;
    }

    @Nested
    @DisplayName("occupyIfAvailable 메서드는")
    class Describe_occupyIfAvailable {

        @Test
        @DisplayName("사용 가능한 세탁기를 통세척 중 상태로 점유해야 한다")
        void it_occupies_available_washer() {
            var machine = createMachine();
            given(machineRepository.findByIdForUpdate(1L)).willReturn(Optional.of(machine));
            given(reservationRepository.countActiveReservationsByMachine(machine, ACTIVE_STATUSES)).willReturn(0L);

            var result = machineGuard.occupyIfAvailable(1L);

            assertThat(result).isPresent();
            assertThat(result.get().deviceId()).isEqualTo("device-1");
            assertThat(machine.getAvailability()).isEqualTo(MachineAvailability.CLEANING);
        }

        @Test
        @DisplayName("활성 예약이 생긴 세탁기는 점유하지 않아야 한다")
        void it_skips_washer_with_active_reservation() {
            var machine = createMachine();
            given(machineRepository.findByIdForUpdate(1L)).willReturn(Optional.of(machine));
            given(reservationRepository.countActiveReservationsByMachine(machine, ACTIVE_STATUSES)).willReturn(1L);

            var result = machineGuard.occupyIfAvailable(1L);

            assertThat(result).isEmpty();
            assertThat(machine.getAvailability()).isEqualTo(MachineAvailability.AVAILABLE);
        }
    }

    @Nested
    @DisplayName("releaseIfNoActiveReservation 메서드는")
    class Describe_releaseIfNoActiveReservation {

        @Test
        @DisplayName("활성 예약이 없는 통세척 중 세탁기의 점유를 해제해야 한다")
        void it_releases_unreserved_washer() {
            var machine = createMachine();
            machine.markAsCleaning();
            given(machineRepository.findByIdForUpdate(1L)).willReturn(Optional.of(machine));
            given(reservationRepository.countActiveReservationsByMachine(machine, ACTIVE_STATUSES)).willReturn(0L);

            var released = machineGuard.releaseIfNoActiveReservation(1L);

            assertThat(released).isTrue();
            assertThat(machine.getAvailability()).isEqualTo(MachineAvailability.AVAILABLE);
        }
    }
}
