package team.washer.server.v2.domain.reservation.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import team.themoment.sdk.exception.ExpectedException;
import team.washer.server.v2.domain.machine.entity.Machine;
import team.washer.server.v2.domain.machine.enums.MachineAvailability;
import team.washer.server.v2.domain.machine.enums.MachineStatus;
import team.washer.server.v2.domain.machine.enums.MachineType;
import team.washer.server.v2.domain.smartthings.dto.response.SmartThingsDeviceStatusResDto;
import team.washer.server.v2.domain.smartthings.dto.response.SmartThingsDeviceStatusResDto.AttributeState;
import team.washer.server.v2.domain.smartthings.dto.response.SmartThingsDeviceStatusResDto.ComponentStatus;
import team.washer.server.v2.domain.smartthings.dto.response.SmartThingsDeviceStatusResDto.DryerOperatingState;
import team.washer.server.v2.domain.smartthings.dto.response.SmartThingsDeviceStatusResDto.SwitchCapability;
import team.washer.server.v2.domain.smartthings.dto.response.SmartThingsDeviceStatusResDto.WasherOperatingState;
import team.washer.server.v2.domain.smartthings.support.DeviceStatusQuerySupport;

@ExtendWith(MockitoExtension.class)
class ReservationDeviceStateVerifierTest {

    private static final String DEVICE_ID = "device-1";
    private static final String UNAVAILABLE_MESSAGE = "기기 상태를 확인할 수 없습니다. 잠시 후 다시 시도해 주세요.";

    @Mock
    private DeviceStatusQuerySupport deviceStatusQuerySupport;

    private ReservationDeviceStateVerifier verifier;

    @BeforeEach
    void setUp() {
        verifier = new ReservationDeviceStateVerifier(deviceStatusQuerySupport);
    }

    private Machine machine(final MachineType type) {
        return Machine.builder().name("세탁기-1").type(type).deviceId(DEVICE_ID).status(MachineStatus.NORMAL)
                .availability(MachineAvailability.AVAILABLE).build();
    }

    private AttributeState attribute(final String value) {
        return value == null ? null : new AttributeState(value, "2026-09-16T01:00:00.000Z", null);
    }

    private SmartThingsDeviceStatusResDto washerStatus(final String machineState, final String switchState) {
        final var component = new ComponentStatus(new WasherOperatingState(attribute(machineState), null, null),
                null,
                new SwitchCapability(attribute(switchState)),
                null);
        return new SmartThingsDeviceStatusResDto(Map.of("main", component));
    }

    private SmartThingsDeviceStatusResDto dryerStatus(final String machineState) {
        final var component = new ComponentStatus(null,
                new DryerOperatingState(attribute(machineState), null, null),
                new SwitchCapability(attribute("on")),
                null);
        return new SmartThingsDeviceStatusResDto(Map.of("main", component));
    }

    private void assertServiceUnavailable(final Machine machine) {
        assertThatThrownBy(() -> verifier.verifyNotOperating(machine)).isInstanceOf(ExpectedException.class)
                .hasMessage(UNAVAILABLE_MESSAGE).satisfies(e -> assertThat(((ExpectedException) e).getStatusCode())
                        .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
    }

    @Nested
    @DisplayName("기기 작동 상태 확인")
    class VerifyNotOperatingTest {

        @ParameterizedTest
        @ValueSource(strings = {"run", "pause", "RUN"})
        @DisplayName("기기가 run 또는 pause 상태이면 CONFLICT로 예약을 거부한다")
        void verifyNotOperating_ShouldThrowConflict_WhenMachineIsOperating(final String machineState) {
            // Given
            given(deviceStatusQuerySupport.queryDeviceStatus(DEVICE_ID)).willReturn(washerStatus(machineState, "on"));

            // When & Then
            assertThatThrownBy(() -> verifier.verifyNotOperating(machine(MachineType.WASHER)))
                    .isInstanceOf(ExpectedException.class).hasMessageContaining("작동 중")
                    .satisfies(e -> assertThat(((ExpectedException) e).getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
        }

        @Test
        @DisplayName("기기가 stop 상태이면 예약을 허용한다")
        void verifyNotOperating_ShouldPass_WhenMachineIsStopped() {
            // Given
            given(deviceStatusQuerySupport.queryDeviceStatus(DEVICE_ID)).willReturn(washerStatus("stop", "on"));

            // When & Then
            assertThatCode(() -> verifier.verifyNotOperating(machine(MachineType.WASHER))).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("전원이 꺼진 기기는 machineState가 없어도 예약을 허용한다")
        void verifyNotOperating_ShouldPass_WhenSwitchIsOff() {
            // Given
            given(deviceStatusQuerySupport.queryDeviceStatus(DEVICE_ID)).willReturn(washerStatus(null, "off"));

            // When & Then
            assertThatCode(() -> verifier.verifyNotOperating(machine(MachineType.WASHER))).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("건조기는 dryerOperatingState를 기준으로 작동 여부를 판정한다")
        void verifyNotOperating_ShouldUseDryerState_WhenMachineIsDryer() {
            // Given
            given(deviceStatusQuerySupport.queryDeviceStatus(DEVICE_ID)).willReturn(dryerStatus("run"));

            // When & Then
            assertThatThrownBy(() -> verifier.verifyNotOperating(machine(MachineType.DRYER)))
                    .isInstanceOf(ExpectedException.class)
                    .satisfies(e -> assertThat(((ExpectedException) e).getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
        }

        @Test
        @DisplayName("세탁기 capability만 보고하는 기기를 건조기로 조회하면 상태 불명으로 거부한다")
        void verifyNotOperating_ShouldThrowServiceUnavailable_WhenCapabilityMismatch() {
            // Given
            given(deviceStatusQuerySupport.queryDeviceStatus(DEVICE_ID)).willReturn(washerStatus("stop", "on"));

            // When & Then
            assertServiceUnavailable(machine(MachineType.DRYER));
        }

        @ParameterizedTest
        @ValueSource(strings = {"unknown-state", " "})
        @DisplayName("알 수 없는 machineState이면 SERVICE_UNAVAILABLE로 거부한다")
        void verifyNotOperating_ShouldThrowServiceUnavailable_WhenStateUnknown(final String machineState) {
            // Given
            given(deviceStatusQuerySupport.queryDeviceStatus(DEVICE_ID)).willReturn(washerStatus(machineState, "on"));

            // When & Then
            assertServiceUnavailable(machine(MachineType.WASHER));
        }

        @Test
        @DisplayName("machineState 응답이 누락되면 SERVICE_UNAVAILABLE로 거부한다")
        void verifyNotOperating_ShouldThrowServiceUnavailable_WhenStateMissing() {
            // Given
            given(deviceStatusQuerySupport.queryDeviceStatus(DEVICE_ID))
                    .willReturn(new SmartThingsDeviceStatusResDto(Map.of()));

            // When & Then
            assertServiceUnavailable(machine(MachineType.WASHER));
        }

        @Test
        @DisplayName("상태 응답 자체가 없으면 SERVICE_UNAVAILABLE로 거부한다")
        void verifyNotOperating_ShouldThrowServiceUnavailable_WhenResponseIsNull() {
            // Given
            given(deviceStatusQuerySupport.queryDeviceStatus(DEVICE_ID)).willReturn(null);

            // When & Then
            assertServiceUnavailable(machine(MachineType.WASHER));
        }

        @Test
        @DisplayName("외부 조회가 실패하거나 타임아웃되면 SERVICE_UNAVAILABLE로 거부한다")
        void verifyNotOperating_ShouldThrowServiceUnavailable_WhenQueryFails() {
            // Given
            given(deviceStatusQuerySupport.queryDeviceStatus(DEVICE_ID))
                    .willThrow(new ExpectedException("기기 상태 조회에 실패했습니다: Read timed out", HttpStatus.BAD_GATEWAY));

            // When & Then
            assertServiceUnavailable(machine(MachineType.WASHER));
        }

        @Test
        @DisplayName("SmartThings 토큰이 없어 조회할 수 없어도 SERVICE_UNAVAILABLE로 거부한다")
        void verifyNotOperating_ShouldThrowServiceUnavailable_WhenTokenUnavailable() {
            // Given
            given(deviceStatusQuerySupport.queryDeviceStatus(DEVICE_ID))
                    .willThrow(new IllegalStateException("token missing"));

            // When & Then
            assertServiceUnavailable(machine(MachineType.WASHER));
        }
    }
}
