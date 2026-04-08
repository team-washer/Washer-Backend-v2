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
import team.washer.server.v2.domain.machine.dto.request.UpdateMachineReqDto;
import team.washer.server.v2.domain.machine.entity.Machine;
import team.washer.server.v2.domain.machine.enums.MachineAvailability;
import team.washer.server.v2.domain.machine.enums.MachineStatus;
import team.washer.server.v2.domain.machine.enums.MachineType;
import team.washer.server.v2.domain.machine.enums.Position;
import team.washer.server.v2.domain.machine.repository.MachineRepository;
import team.washer.server.v2.domain.machine.service.impl.UpdateMachineServiceImpl;

@ExtendWith(MockitoExtension.class)
@DisplayName("UpdateMachineServiceImpl 클래스의")
class UpdateMachineServiceTest {

    @InjectMocks
    private UpdateMachineServiceImpl updateMachineService;

    @Mock
    private MachineRepository machineRepository;

    private Machine createMachine() {
        return Machine.builder().name("W-2F-L1").type(MachineType.WASHER).deviceId("device-1").floor(2)
                .position(Position.LEFT).number(1).status(MachineStatus.NORMAL)
                .availability(MachineAvailability.AVAILABLE).build();
    }

    @Nested
    @DisplayName("execute 메서드는")
    class Describe_execute {

        @Nested
        @DisplayName("Device ID만 변경하는 요청일 때")
        class Context_with_device_id_change {

            @Test
            @DisplayName("Device ID가 변경된 기기 정보를 반환해야 한다")
            void it_updates_device_id() {
                // Given
                var machineId = 1L;
                var machine = createMachine();
                var reqDto = new UpdateMachineReqDto(null, null, null, null, "new-device-id");

                given(machineRepository.findById(machineId)).willReturn(Optional.of(machine));
                given(machineRepository.findByDeviceId("new-device-id")).willReturn(Optional.empty());

                // When
                var result = updateMachineService.execute(machineId, reqDto);

                // Then
                assertThat(result).isNotNull();
                assertThat(result.deviceId()).isEqualTo("new-device-id");
            }
        }

        @Nested
        @DisplayName("위치 정보를 변경하는 요청일 때")
        class Context_with_location_change {

            @Test
            @DisplayName("위치와 이름이 변경된 기기 정보를 반환해야 한다")
            void it_updates_location_and_name() {
                // Given
                var machineId = 1L;
                var machine = createMachine();
                var reqDto = new UpdateMachineReqDto(MachineType.WASHER, 3, Position.RIGHT, 2, null);

                given(machineRepository.findById(machineId)).willReturn(Optional.of(machine));
                given(machineRepository.findByLocation(MachineType.WASHER, 3, Position.RIGHT, 2))
                        .willReturn(Optional.empty());

                // When
                var result = updateMachineService.execute(machineId, reqDto);

                // Then
                assertThat(result).isNotNull();
                assertThat(result.floor()).isEqualTo(3);
                assertThat(result.position()).isEqualTo(Position.RIGHT);
                assertThat(result.number()).isEqualTo(2);
            }
        }

        @Nested
        @DisplayName("이미 다른 기기에서 사용 중인 Device ID로 변경하려 할 때")
        class Context_with_duplicate_device_id {

            @Test
            @DisplayName("ExpectedException이 발생하고 CONFLICT 상태를 반환해야 한다")
            void it_throws_conflict_exception() {
                // Given
                var machineId = 1L;
                var machine = createMachine();
                var otherMachine = Machine.builder().name("D-3F-R1").type(MachineType.DRYER).deviceId("existing-device")
                        .floor(3).position(Position.RIGHT).number(1).status(MachineStatus.NORMAL)
                        .availability(MachineAvailability.AVAILABLE).build();
                var reqDto = new UpdateMachineReqDto(null, null, null, null, "existing-device");

                given(machineRepository.findById(machineId)).willReturn(Optional.of(machine));
                given(machineRepository.findByDeviceId("existing-device")).willReturn(Optional.of(otherMachine));

                // When & Then
                assertThatThrownBy(() -> updateMachineService.execute(machineId, reqDto))
                        .isInstanceOf(ExpectedException.class).hasMessage("이미 다른 기기에서 사용 중인 Device ID입니다")
                        .satisfies(e -> assertThat(((ExpectedException) e).getStatusCode())
                                .isEqualTo(HttpStatus.CONFLICT));
            }
        }

        @Nested
        @DisplayName("이미 다른 기기가 있는 위치로 변경하려 할 때")
        class Context_with_duplicate_location {

            @Test
            @DisplayName("ExpectedException이 발생하고 CONFLICT 상태를 반환해야 한다")
            void it_throws_conflict_exception_for_location() {
                // Given
                var machineId = 1L;
                var machine = createMachine();
                var otherMachine = Machine.builder().name("W-3F-R1").type(MachineType.WASHER).deviceId("other-device")
                        .floor(3).position(Position.RIGHT).number(1).status(MachineStatus.NORMAL)
                        .availability(MachineAvailability.AVAILABLE).build();
                var reqDto = new UpdateMachineReqDto(MachineType.WASHER, 3, Position.RIGHT, 1, null);

                given(machineRepository.findById(machineId)).willReturn(Optional.of(machine));
                given(machineRepository.findByLocation(MachineType.WASHER, 3, Position.RIGHT, 1))
                        .willReturn(Optional.of(otherMachine));

                // When & Then
                assertThatThrownBy(() -> updateMachineService.execute(machineId, reqDto))
                        .isInstanceOf(ExpectedException.class).hasMessage("해당 위치에 이미 다른 기기가 등록되어 있습니다")
                        .satisfies(e -> assertThat(((ExpectedException) e).getStatusCode())
                                .isEqualTo(HttpStatus.CONFLICT));
            }
        }

        @Nested
        @DisplayName("존재하지 않는 기기를 수정하려 할 때")
        class Context_with_nonexistent_machine {

            @Test
            @DisplayName("ExpectedException이 발생하고 NOT_FOUND 상태를 반환해야 한다")
            void it_throws_not_found_exception() {
                // Given
                var machineId = 999L;
                var reqDto = new UpdateMachineReqDto(null, null, null, null, "new-device");
                given(machineRepository.findById(machineId)).willReturn(Optional.empty());

                // When & Then
                assertThatThrownBy(() -> updateMachineService.execute(machineId, reqDto))
                        .isInstanceOf(ExpectedException.class).hasMessage("기기를 찾을 수 없습니다")
                        .satisfies(e -> assertThat(((ExpectedException) e).getStatusCode())
                                .isEqualTo(HttpStatus.NOT_FOUND));
            }
        }
    }
}
