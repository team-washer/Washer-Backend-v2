package team.washer.server.v2.domain.machine.entity;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import team.washer.server.v2.domain.machine.enums.MachineAvailability;
import team.washer.server.v2.domain.machine.enums.MachineStatus;
import team.washer.server.v2.domain.machine.enums.MachineType;
import team.washer.server.v2.domain.machine.enums.Position;

@DisplayName("Machine 클래스의")
class MachineTest {

    private Machine createMachine() {
        return Machine.builder().name("W-2F-L1").type(MachineType.WASHER).deviceId("device-1").floor(2)
                .position(Position.LEFT).number(1).status(MachineStatus.NORMAL)
                .availability(MachineAvailability.AVAILABLE).build();
    }

    @Nested
    @DisplayName("releaseIfHeld 메서드는")
    class Describe_releaseIfHeld {

        @Test
        @DisplayName("예약됨 상태의 기기를 사용 가능 상태로 해제해야 한다")
        void it_releases_reserved_machine() {
            // Given
            final var machine = createMachine();
            machine.markAsReserved();

            // When
            machine.releaseIfHeld();

            // Then
            assertThat(machine.getAvailability()).isEqualTo(MachineAvailability.AVAILABLE);
        }

        @Test
        @DisplayName("사용 중 상태의 기기를 사용 가능 상태로 해제해야 한다")
        void it_releases_in_use_machine() {
            // Given
            final var machine = createMachine();
            machine.markAsInUse();

            // When
            machine.releaseIfHeld();

            // Then
            assertThat(machine.getAvailability()).isEqualTo(MachineAvailability.AVAILABLE);
        }

        @Test
        @DisplayName("고장 처리된 기기의 상태를 그대로 유지해야 한다")
        void it_keeps_malfunction_machine_unavailable() {
            // Given
            final var machine = createMachine();
            machine.markAsMalfunction();

            // When
            machine.releaseIfHeld();

            // Then
            assertThat(machine.getStatus()).isEqualTo(MachineStatus.MALFUNCTION);
            assertThat(machine.getAvailability()).isEqualTo(MachineAvailability.UNAVAILABLE);
        }

        @Test
        @DisplayName("사용 불가로 처리된 정상 기기의 상태를 그대로 유지해야 한다")
        void it_keeps_unavailable_normal_machine_unavailable() {
            // Given
            final var machine = createMachine();
            machine.markAsUnavailable();

            // When
            machine.releaseIfHeld();

            // Then
            assertThat(machine.getStatus()).isEqualTo(MachineStatus.NORMAL);
            assertThat(machine.getAvailability()).isEqualTo(MachineAvailability.UNAVAILABLE);
        }

        @Test
        @DisplayName("이미 사용 가능한 기기의 상태를 그대로 유지해야 한다")
        void it_keeps_available_machine_available() {
            // Given
            final var machine = createMachine();

            // When
            machine.releaseIfHeld();

            // Then
            assertThat(machine.getAvailability()).isEqualTo(MachineAvailability.AVAILABLE);
        }
    }
}
