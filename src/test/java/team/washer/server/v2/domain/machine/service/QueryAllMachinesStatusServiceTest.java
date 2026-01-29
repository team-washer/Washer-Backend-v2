package team.washer.server.v2.domain.machine.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import team.washer.server.v2.domain.machine.entity.Machine;
import team.washer.server.v2.domain.machine.enums.MachineAvailability;
import team.washer.server.v2.domain.machine.enums.MachineStatus;
import team.washer.server.v2.domain.machine.enums.MachineType;
import team.washer.server.v2.domain.machine.enums.Position;
import team.washer.server.v2.domain.machine.repository.MachineRepository;
import team.washer.server.v2.domain.machine.service.impl.QueryAllMachinesStatusServiceImpl;
import team.washer.server.v2.domain.reservation.entity.Reservation;
import team.washer.server.v2.domain.reservation.repository.ReservationRepository;
import team.washer.server.v2.domain.smartthings.dto.response.SmartThingsDeviceStatusResDto;
import team.washer.server.v2.domain.smartthings.service.QueryAllDevicesStatusService;

@ExtendWith(MockitoExtension.class)
class QueryAllMachinesStatusServiceTest {

    @InjectMocks
    private QueryAllMachinesStatusServiceImpl queryAllMachinesStatusService;

    @Mock
    private MachineRepository machineRepository;

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private QueryAllDevicesStatusService queryAllDevicesStatusService;

    @Mock
    private Reservation reservation;

    @Nested
    @DisplayName("전체 기기 상태 조회")
    class ExecuteTest {

        @Test
        @DisplayName("기기 목록과 SmartThings 상태를 성공적으로 조회한다")
        void execute_ShouldReturnMachinesStatus_WhenMachinesExist() {
            // Given
            var machine1 = Machine.builder().name("W-3F-L1").type(MachineType.WASHER).deviceId("device-1").floor(3)
                    .position(Position.LEFT).number(1).status(MachineStatus.NORMAL)
                    .availability(MachineAvailability.AVAILABLE).build();

            var machine2 = Machine.builder().name("D-3F-R1").type(MachineType.DRYER).deviceId("device-2").floor(3)
                    .position(Position.RIGHT).number(1).status(MachineStatus.NORMAL)
                    .availability(MachineAvailability.IN_USE).build();

            when(machineRepository.findAll()).thenReturn(List.of(machine1, machine2));

            // SmartThings 상태 Mock
            var completionTimeValue = new SmartThingsDeviceStatusResDto.Value("2026-01-26T15:30:00Z",
                    "2026-01-26T14:30:00Z",
                    null);
            var completionTimeCapability = new SmartThingsDeviceStatusResDto.CapabilityStatus(completionTimeValue);
            var washerJobStateValue = new SmartThingsDeviceStatusResDto.Value("run", "2026-01-26T14:00:00Z", null);
            var washerJobStateCapability = new SmartThingsDeviceStatusResDto.CapabilityStatus(washerJobStateValue);

            var componentStatus = new SmartThingsDeviceStatusResDto.ComponentStatus(null,
                    null,
                    washerJobStateCapability,
                    null,
                    completionTimeCapability,
                    null);
            var deviceStatus = new SmartThingsDeviceStatusResDto(Map.of("main", componentStatus));

            when(queryAllDevicesStatusService.execute(List.of("device-1", "device-2")))
                    .thenReturn(Map.of("device-1", deviceStatus, "device-2", deviceStatus));

            when(reservationRepository.findActiveReservationByMachineId(any())).thenReturn(Optional.empty());

            // When
            var result = queryAllMachinesStatusService.execute();

            // Then
            assertThat(result).hasSize(2);
            assertThat(result.getFirst().name()).isEqualTo("W-3F-L1");
            assertThat(result.getFirst().jobState()).isEqualTo("run");
            assertThat(result.getFirst().reservationId()).isNull();
        }

        @Test
        @DisplayName("기기가 없으면 빈 리스트를 반환한다")
        void execute_ShouldReturnEmptyList_WhenNoMachinesExist() {
            // Given
            when(machineRepository.findAll()).thenReturn(List.of());

            // When
            var result = queryAllMachinesStatusService.execute();

            // Then
            assertThat(result).isEmpty();
        }
    }
}
