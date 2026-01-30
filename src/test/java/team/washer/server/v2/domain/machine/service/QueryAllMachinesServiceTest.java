package team.washer.server.v2.domain.machine.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import team.washer.server.v2.domain.machine.dto.response.MachineListResDto;
import team.washer.server.v2.domain.machine.entity.Machine;
import team.washer.server.v2.domain.machine.enums.MachineAvailability;
import team.washer.server.v2.domain.machine.enums.MachineStatus;
import team.washer.server.v2.domain.machine.enums.MachineType;
import team.washer.server.v2.domain.machine.enums.Position;
import team.washer.server.v2.domain.machine.repository.MachineRepository;
import team.washer.server.v2.domain.machine.service.impl.QueryAllMachinesServiceImpl;

@ExtendWith(MockitoExtension.class)
@DisplayName("QueryAllMachinesServiceImpl 클래스의")
class QueryAllMachinesServiceTest {

    @InjectMocks
    private QueryAllMachinesServiceImpl queryAllMachinesService;

    @Mock
    private MachineRepository machineRepository;

    private Machine createMachine(String name, MachineType type, Integer floor, MachineStatus status) {
        return Machine.builder().name(name).type(type).deviceId("device-" + name).floor(floor).position(Position.LEFT)
                .number(1).status(status).availability(MachineAvailability.AVAILABLE).build();
    }

    @Nested
    @DisplayName("execute 메서드는")
    class Describe_execute {

        @Nested
        @DisplayName("이름으로 필터링할 때")
        class Context_with_name_filter {

            @Test
            @DisplayName("이름을 포함하는 기기 목록을 반환해야 한다")
            void it_returns_machines_by_name() {
                // Given
                String searchName = "W-2F";
                Machine machine1 = createMachine("W-2F-L1", MachineType.WASHER, 2, MachineStatus.NORMAL);
                Machine machine2 = createMachine("W-2F-R1", MachineType.WASHER, 2, MachineStatus.NORMAL);
                List<Machine> machines = Arrays.asList(machine1, machine2);
                Page<Machine> machinePage = new PageImpl<>(machines, PageRequest.of(0, 10), machines.size());

                given(machineRepository
                        .findAllWithFilters(eq(searchName), eq(null), eq(null), eq(null), any(Pageable.class)))
                        .willReturn(machinePage);

                // When
                MachineListResDto result = queryAllMachinesService
                        .execute(searchName, null, null, null, PageRequest.of(0, 10));

                // Then
                assertThat(result.totalCount()).isEqualTo(2);
                assertThat(result.machines()).allMatch(m -> m.name().contains("W-2F"));
                then(machineRepository).should(times(1))
                        .findAllWithFilters(eq(searchName), eq(null), eq(null), eq(null), any(Pageable.class));
            }
        }

        @Nested
        @DisplayName("기기 유형으로 필터링할 때")
        class Context_with_type_filter {

            @Test
            @DisplayName("해당 유형의 기기 목록을 반환해야 한다")
            void it_returns_machines_by_type() {
                // Given
                MachineType type = MachineType.WASHER;
                Machine machine = createMachine("W-2F-L1", MachineType.WASHER, 2, MachineStatus.NORMAL);
                List<Machine> machines = List.of(machine);
                Page<Machine> machinePage = new PageImpl<>(machines, PageRequest.of(0, 10), machines.size());

                given(machineRepository.findAllWithFilters(eq(null), eq(type), eq(null), eq(null), any(Pageable.class)))
                        .willReturn(machinePage);

                // When
                MachineListResDto result = queryAllMachinesService
                        .execute(null, type, null, null, PageRequest.of(0, 10));

                // Then
                assertThat(result.totalCount()).isEqualTo(1);
                assertThat(result.machines().get(0).type()).isEqualTo(MachineType.WASHER);
                then(machineRepository).should(times(1))
                        .findAllWithFilters(eq(null), eq(type), eq(null), eq(null), any(Pageable.class));
            }
        }

        @Nested
        @DisplayName("층으로 필터링할 때")
        class Context_with_floor_filter {

            @Test
            @DisplayName("해당 층의 기기 목록을 반환해야 한다")
            void it_returns_machines_by_floor() {
                // Given
                Integer floor = 3;
                Machine machine = createMachine("W-3F-L1", MachineType.WASHER, 3, MachineStatus.NORMAL);
                List<Machine> machines = List.of(machine);
                Page<Machine> machinePage = new PageImpl<>(machines, PageRequest.of(0, 10), machines.size());

                given(machineRepository
                        .findAllWithFilters(eq(null), eq(null), eq(floor), eq(null), any(Pageable.class)))
                        .willReturn(machinePage);

                // When
                MachineListResDto result = queryAllMachinesService
                        .execute(null, null, floor, null, PageRequest.of(0, 10));

                // Then
                assertThat(result.totalCount()).isEqualTo(1);
                assertThat(result.machines().get(0).floor()).isEqualTo(3);
                then(machineRepository).should(times(1))
                        .findAllWithFilters(eq(null), eq(null), eq(floor), eq(null), any(Pageable.class));
            }
        }

        @Nested
        @DisplayName("상태로 필터링할 때")
        class Context_with_status_filter {

            @Test
            @DisplayName("해당 상태의 기기 목록을 반환해야 한다")
            void it_returns_machines_by_status() {
                // Given
                MachineStatus status = MachineStatus.MALFUNCTION;
                Machine machine = createMachine("W-2F-L1", MachineType.WASHER, 2, MachineStatus.MALFUNCTION);
                List<Machine> machines = List.of(machine);
                Page<Machine> machinePage = new PageImpl<>(machines, PageRequest.of(0, 10), machines.size());

                given(machineRepository
                        .findAllWithFilters(eq(null), eq(null), eq(null), eq(status), any(Pageable.class)))
                        .willReturn(machinePage);

                // When
                MachineListResDto result = queryAllMachinesService
                        .execute(null, null, null, status, PageRequest.of(0, 10));

                // Then
                assertThat(result.totalCount()).isEqualTo(1);
                assertThat(result.machines().get(0).status()).isEqualTo(MachineStatus.MALFUNCTION);
                then(machineRepository).should(times(1))
                        .findAllWithFilters(eq(null), eq(null), eq(null), eq(status), any(Pageable.class));
            }
        }

        @Nested
        @DisplayName("필터가 없을 때")
        class Context_without_filter {

            @Test
            @DisplayName("모든 기기 목록을 반환해야 한다")
            void it_returns_all_machines() {
                // Given
                Machine machine1 = createMachine("W-2F-L1", MachineType.WASHER, 2, MachineStatus.NORMAL);
                Machine machine2 = createMachine("D-3F-R1", MachineType.DRYER, 3, MachineStatus.NORMAL);
                List<Machine> machines = Arrays.asList(machine1, machine2);
                Page<Machine> machinePage = new PageImpl<>(machines, PageRequest.of(0, 10), machines.size());

                given(machineRepository.findAllWithFilters(eq(null), eq(null), eq(null), eq(null), any(Pageable.class)))
                        .willReturn(machinePage);

                // When
                MachineListResDto result = queryAllMachinesService
                        .execute(null, null, null, null, PageRequest.of(0, 10));

                // Then
                assertThat(result.totalCount()).isEqualTo(2);
                then(machineRepository).should(times(1))
                        .findAllWithFilters(eq(null), eq(null), eq(null), eq(null), any(Pageable.class));
            }
        }

        @Nested
        @DisplayName("여러 조건으로 동시에 필터링할 때")
        class Context_with_multiple_filters {

            @Test
            @DisplayName("모든 조건을 AND로 조합하여 기기를 반환해야 한다")
            void it_returns_machines_matching_all_conditions() {
                // Given
                String name = "W-2F";
                MachineType type = MachineType.WASHER;
                Integer floor = 2;
                MachineStatus status = MachineStatus.NORMAL;
                Machine machine = createMachine("W-2F-L1", MachineType.WASHER, 2, MachineStatus.NORMAL);
                List<Machine> machines = List.of(machine);
                Page<Machine> machinePage = new PageImpl<>(machines, PageRequest.of(0, 10), machines.size());

                given(machineRepository
                        .findAllWithFilters(eq(name), eq(type), eq(floor), eq(status), any(Pageable.class)))
                        .willReturn(machinePage);

                // When
                MachineListResDto result = queryAllMachinesService
                        .execute(name, type, floor, status, PageRequest.of(0, 10));

                // Then
                assertThat(result.totalCount()).isEqualTo(1);
                assertThat(result.machines().get(0).name()).contains("W-2F");
                assertThat(result.machines().get(0).type()).isEqualTo(MachineType.WASHER);
                assertThat(result.machines().get(0).floor()).isEqualTo(2);
                assertThat(result.machines().get(0).status()).isEqualTo(MachineStatus.NORMAL);
                then(machineRepository).should(times(1))
                        .findAllWithFilters(eq(name), eq(type), eq(floor), eq(status), any(Pageable.class));
            }
        }
    }
}
