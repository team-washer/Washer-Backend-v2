package team.washer.server.v2.domain.smartthings.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
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

import team.themoment.sdk.exception.ExpectedException;
import team.washer.server.v2.domain.machine.entity.Machine;
import team.washer.server.v2.domain.machine.enums.MachineAvailability;
import team.washer.server.v2.domain.machine.enums.MachineStatus;
import team.washer.server.v2.domain.machine.enums.MachineType;
import team.washer.server.v2.domain.machine.enums.Position;
import team.washer.server.v2.domain.machine.repository.MachineRepository;
import team.washer.server.v2.domain.smartthings.dto.response.SmartThingsDeviceListResDto;
import team.washer.server.v2.domain.smartthings.entity.SmartThingsToken;
import team.washer.server.v2.domain.smartthings.repository.SmartThingsTokenRepository;
import team.washer.server.v2.domain.smartthings.service.impl.SyncSmartThingsDevicesServiceImpl;
import team.washer.server.v2.global.thirdparty.smartthings.feign.SmartThingsFeignClient;

@ExtendWith(MockitoExtension.class)
@DisplayName("SyncSmartThingsDevicesServiceImpl 클래스의")
class SyncSmartThingsDevicesServiceTest {

    @InjectMocks
    private SyncSmartThingsDevicesServiceImpl syncSmartThingsDevicesService;

    @Mock
    private SmartThingsFeignClient feignClient;

    @Mock
    private SmartThingsTokenRepository tokenRepository;

    @Mock
    private MachineRepository machineRepository;

    private SmartThingsToken createValidToken() {
        return SmartThingsToken.builder().accessToken("valid-access-token").refreshToken("refresh-token")
                .expiresAt(LocalDateTime.now().plusHours(1)).build();
    }

    private SmartThingsToken createExpiredToken() {
        return SmartThingsToken.builder().accessToken("expired-access-token").refreshToken("refresh-token")
                .expiresAt(LocalDateTime.now().minusMinutes(10)).build();
    }

    @Nested
    @DisplayName("execute 메서드는")
    class Describe_execute {

        @Nested
        @DisplayName("신규 기기가 있는 SmartThings 목록을 동기화할 때")
        class Context_with_new_devices {

            @Test
            @DisplayName("신규 기기를 저장해야 한다")
            void it_saves_new_machines() {
                // Given
                var token = createValidToken();
                var deviceList = new SmartThingsDeviceListResDto(
                        List.of(new SmartThingsDeviceListResDto.DeviceItem("device-1", "Washer-2F-L1", "Washer")));

                given(tokenRepository.findSingletonToken()).willReturn(Optional.of(token));
                given(feignClient.getDeviceList("Bearer valid-access-token")).willReturn(deviceList);
                given(machineRepository.findByName("Washer-2F-L1")).willReturn(Optional.empty());
                given(machineRepository.findAll()).willReturn(List.of());

                // When
                syncSmartThingsDevicesService.execute();

                // Then
                then(machineRepository).should(times(1)).save(any(Machine.class));
            }
        }

        @Nested
        @DisplayName("이미 존재하는 기기의 deviceId가 변경된 경우")
        class Context_with_changed_device_id {

            @Test
            @DisplayName("기존 기기의 deviceId를 갱신해야 한다")
            void it_updates_existing_machine_device_id() {
                // Given
                var token = createValidToken();
                var existingMachine = Machine.builder().name("W-2F-L1").type(MachineType.WASHER)
                        .deviceId("old-device-id").floor(2).position(Position.LEFT).number(1)
                        .status(MachineStatus.NORMAL).availability(MachineAvailability.AVAILABLE).build();
                var deviceList = new SmartThingsDeviceListResDto(
                        List.of(new SmartThingsDeviceListResDto.DeviceItem("new-device-id", "Washer-2F-L1", "Washer")));

                given(tokenRepository.findSingletonToken()).willReturn(Optional.of(token));
                given(feignClient.getDeviceList("Bearer valid-access-token")).willReturn(deviceList);
                given(machineRepository.findByName("Washer-2F-L1")).willReturn(Optional.of(existingMachine));
                given(machineRepository.findAll()).willReturn(List.of(existingMachine));

                // When
                syncSmartThingsDevicesService.execute();

                // Then
                assertThat(existingMachine.getDeviceId()).isEqualTo("new-device-id");
                then(machineRepository).should(never()).save(any(Machine.class));
            }
        }

        @Nested
        @DisplayName("SmartThings에서 사라진 기기가 있을 때")
        class Context_with_missing_devices {

            @Test
            @DisplayName("해당 기기를 UNAVAILABLE 상태로 변경해야 한다")
            void it_marks_missing_machines_unavailable() {
                // Given
                var token = createValidToken();
                var missingMachine = Machine.builder().name("W-3F-R1").type(MachineType.WASHER)
                        .deviceId("missing-device").floor(3).position(Position.RIGHT).number(1)
                        .status(MachineStatus.NORMAL).availability(MachineAvailability.AVAILABLE).build();
                var deviceList = new SmartThingsDeviceListResDto(
                        List.of(new SmartThingsDeviceListResDto.DeviceItem("device-1", "Washer-2F-L1", "Washer")));

                given(tokenRepository.findSingletonToken()).willReturn(Optional.of(token));
                given(feignClient.getDeviceList("Bearer valid-access-token")).willReturn(deviceList);
                given(machineRepository.findByName("Washer-2F-L1")).willReturn(Optional.empty());
                given(machineRepository.findAll()).willReturn(List.of(missingMachine));

                // When
                syncSmartThingsDevicesService.execute();

                // Then
                assertThat(missingMachine.getAvailability()).isEqualTo(MachineAvailability.UNAVAILABLE);
            }
        }

        @Nested
        @DisplayName("저장된 토큰이 없을 때")
        class Context_with_no_token {

            @Test
            @DisplayName("ExpectedException이 발생하고 NOT_FOUND 상태를 반환해야 한다")
            void it_throws_not_found_exception() {
                // Given
                given(tokenRepository.findSingletonToken()).willReturn(Optional.empty());

                // When & Then
                assertThatThrownBy(() -> syncSmartThingsDevicesService.execute()).isInstanceOf(ExpectedException.class)
                        .hasMessage("SmartThings 토큰이 존재하지 않습니다")
                        .satisfies(e -> assertThat(((ExpectedException) e).getStatusCode())
                                .isEqualTo(HttpStatus.NOT_FOUND));

                then(feignClient).shouldHaveNoInteractions();
            }
        }

        @Nested
        @DisplayName("만료된 토큰으로 동기화하려 할 때")
        class Context_with_expired_token {

            @Test
            @DisplayName("ExpectedException이 발생하고 UNAUTHORIZED 상태를 반환해야 한다")
            void it_throws_unauthorized_exception() {
                // Given
                var expiredToken = createExpiredToken();
                given(tokenRepository.findSingletonToken()).willReturn(Optional.of(expiredToken));

                // When & Then
                assertThatThrownBy(() -> syncSmartThingsDevicesService.execute()).isInstanceOf(ExpectedException.class)
                        .hasMessage("SmartThings 토큰이 만료되었거나 유효하지 않습니다")
                        .satisfies(e -> assertThat(((ExpectedException) e).getStatusCode())
                                .isEqualTo(HttpStatus.UNAUTHORIZED));

                then(feignClient).shouldHaveNoInteractions();
            }
        }

        @Nested
        @DisplayName("잘못된 label 형식의 기기가 포함된 경우")
        class Context_with_invalid_label {

            @Test
            @DisplayName("해당 기기는 건너뛰고 나머지를 처리해야 한다")
            void it_skips_invalid_label_device() {
                // Given
                var token = createValidToken();
                var deviceList = new SmartThingsDeviceListResDto(
                        List.of(new SmartThingsDeviceListResDto.DeviceItem("device-bad", "InvalidLabel", "Unknown")));

                given(tokenRepository.findSingletonToken()).willReturn(Optional.of(token));
                given(feignClient.getDeviceList("Bearer valid-access-token")).willReturn(deviceList);
                given(machineRepository.findAll()).willReturn(List.of());

                // When
                syncSmartThingsDevicesService.execute();

                // Then
                then(machineRepository).should(never()).save(any(Machine.class));
            }
        }
    }
}
