package team.washer.server.v2.domain.machine.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
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
import team.washer.server.v2.domain.machine.dto.request.CreateMachineReqDto;
import team.washer.server.v2.domain.machine.entity.Machine;
import team.washer.server.v2.domain.machine.enums.MachineAvailability;
import team.washer.server.v2.domain.machine.enums.MachineStatus;
import team.washer.server.v2.domain.machine.enums.MachineType;
import team.washer.server.v2.domain.machine.enums.Position;
import team.washer.server.v2.domain.machine.repository.MachineRepository;
import team.washer.server.v2.domain.machine.service.impl.CreateMachineServiceImpl;

@ExtendWith(MockitoExtension.class)
@DisplayName("CreateMachineServiceImpl 클래스의")
class CreateMachineServiceTest {

    @InjectMocks
    private CreateMachineServiceImpl createMachineService;

    @Mock
    private MachineRepository machineRepository;

    private Machine createSavedMachine() {
        return Machine.builder().name("W-2F-L1").type(MachineType.WASHER).deviceId("device-abc").floor(2)
                .position(Position.LEFT).number(1).status(MachineStatus.NORMAL)
                .availability(MachineAvailability.AVAILABLE).build();
    }

    @Nested
    @DisplayName("execute 메서드는")
    class Describe_execute {

        @Nested
        @DisplayName("중복 없는 정상 요청일 때")
        class Context_with_valid_request {

            @Test
            @DisplayName("기기를 생성하고 응답 DTO를 반환해야 한다")
            void it_creates_machine_and_returns_dto() {
                // Given
                var reqDto = new CreateMachineReqDto(MachineType.WASHER, 2, Position.LEFT, 1, "device-abc");
                var savedMachine = createSavedMachine();

                given(machineRepository.existsByDeviceId("device-abc")).willReturn(false);
                given(machineRepository.findByLocation(MachineType.WASHER, 2, Position.LEFT, 1))
                        .willReturn(Optional.empty());
                given(machineRepository.save(any(Machine.class))).willReturn(savedMachine);

                // When
                var result = createMachineService.execute(reqDto);

                // Then
                assertThat(result).isNotNull();
                assertThat(result.name()).isEqualTo("W-2F-L1");
                assertThat(result.type()).isEqualTo(MachineType.WASHER);
                assertThat(result.deviceId()).isEqualTo("device-abc");
                then(machineRepository).should(times(1)).save(any(Machine.class));
            }
        }

        @Nested
        @DisplayName("이미 등록된 Device ID로 요청할 때")
        class Context_with_duplicate_device_id {

            @Test
            @DisplayName("ExpectedException이 발생하고 CONFLICT 상태를 반환해야 한다")
            void it_throws_conflict_exception() {
                // Given
                var reqDto = new CreateMachineReqDto(MachineType.WASHER, 2, Position.LEFT, 1, "device-abc");
                given(machineRepository.existsByDeviceId("device-abc")).willReturn(true);

                // When & Then
                assertThatThrownBy(() -> createMachineService.execute(reqDto))
                        .isInstanceOf(ExpectedException.class)
                        .hasMessage("이미 등록된 Device ID입니다")
                        .satisfies(e -> assertThat(((ExpectedException) e).getStatusCode())
                                .isEqualTo(HttpStatus.CONFLICT));

                then(machineRepository).should(never()).save(any(Machine.class));
            }
        }

        @Nested
        @DisplayName("이미 기기가 등록된 위치로 요청할 때")
        class Context_with_duplicate_location {

            @Test
            @DisplayName("ExpectedException이 발생하고 CONFLICT 상태를 반환해야 한다")
            void it_throws_conflict_exception_for_duplicate_location() {
                // Given
                var reqDto = new CreateMachineReqDto(MachineType.WASHER, 2, Position.LEFT, 1, "new-device");
                var existingMachine = createSavedMachine();

                given(machineRepository.existsByDeviceId("new-device")).willReturn(false);
                given(machineRepository.findByLocation(MachineType.WASHER, 2, Position.LEFT, 1))
                        .willReturn(Optional.of(existingMachine));

                // When & Then
                assertThatThrownBy(() -> createMachineService.execute(reqDto))
                        .isInstanceOf(ExpectedException.class)
                        .hasMessage("해당 위치에 이미 기기가 등록되어 있습니다")
                        .satisfies(e -> assertThat(((ExpectedException) e).getStatusCode())
                                .isEqualTo(HttpStatus.CONFLICT));

                then(machineRepository).should(never()).save(any(Machine.class));
            }
        }
    }
}
