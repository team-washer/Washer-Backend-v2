package team.washer.server.v2.domain.machine.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Map;
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
import team.washer.server.v2.domain.machine.service.impl.QueryMachineDeviceStatusServiceImpl;
import team.washer.server.v2.domain.smartthings.dto.response.SmartThingsDeviceStatusResDto;
import team.washer.server.v2.domain.smartthings.enums.MachineOperatingState;
import team.washer.server.v2.domain.smartthings.support.DeviceStatusQuerySupport;
import team.washer.server.v2.domain.user.entity.User;
import team.washer.server.v2.domain.user.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName("QueryMachineDeviceStatusServiceImpl 클래스의")
class QueryMachineDeviceStatusServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long MACHINE_ID = 10L;
    private static final String DEVICE_ID = "device-1";

    @InjectMocks
    private QueryMachineDeviceStatusServiceImpl queryMachineDeviceStatusService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private MachineRepository machineRepository;

    @Mock
    private DeviceStatusQuerySupport deviceStatusQuerySupport;

    @Mock
    private User user;

    private Machine washer() {
        return Machine.builder().name("W-3F-L1").type(MachineType.WASHER).deviceId(DEVICE_ID).floor(3)
                .position(Position.LEFT).number(1).status(MachineStatus.NORMAL)
                .availability(MachineAvailability.AVAILABLE).build();
    }

    private SmartThingsDeviceStatusResDto runningWasherStatus(String completionTime) {
        var washerOpState = new SmartThingsDeviceStatusResDto.WasherOperatingState(
                new SmartThingsDeviceStatusResDto.AttributeState("run", null, null),
                new SmartThingsDeviceStatusResDto.AttributeState("wash", null, null),
                new SmartThingsDeviceStatusResDto.AttributeState(completionTime, null, null));
        var switchCapability = new SmartThingsDeviceStatusResDto.SwitchCapability(
                new SmartThingsDeviceStatusResDto.AttributeState("on", null, null));
        var component = new SmartThingsDeviceStatusResDto.ComponentStatus(washerOpState, null, switchCapability, null);
        return new SmartThingsDeviceStatusResDto(Map.of("main", component));
    }

    @Nested
    @DisplayName("execute 메서드는")
    class Describe_execute {

        @Nested
        @DisplayName("등록된 기기의 SmartThings 상태 조회에 성공하면")
        class Context_success {

            @Test
            @DisplayName("토큰 없이 작동 상태, 작업 상태, 전원 상태, 완료 예정 시간을 반환한다")
            void returnsDeviceStatus() {
                // Given
                when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
                when(machineRepository.findById(MACHINE_ID)).thenReturn(Optional.of(washer()));
                when(deviceStatusQuerySupport.queryDeviceStatus(DEVICE_ID))
                        .thenReturn(runningWasherStatus("2099-01-01T00:00:00Z"));

                // When
                var result = queryMachineDeviceStatusService.execute(USER_ID, MACHINE_ID);

                // Then
                assertThat(result.operatingState()).isEqualTo(MachineOperatingState.RUN);
                assertThat(result.jobState()).isEqualTo("wash");
                assertThat(result.switchStatus()).isEqualTo("on");
                assertThat(result.expectedCompletionTime()).isNotNull();
                assertThat(result.remainingMinutes()).isPositive();
            }
        }

        @Nested
        @DisplayName("완료 예정 시간이 없으면")
        class Context_noCompletionTime {

            @Test
            @DisplayName("완료 예정 시간과 남은 시간을 null로 반환한다")
            void returnsNullCompletionTime() {
                // Given
                when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
                when(machineRepository.findById(MACHINE_ID)).thenReturn(Optional.of(washer()));
                when(deviceStatusQuerySupport.queryDeviceStatus(DEVICE_ID)).thenReturn(runningWasherStatus(null));

                // When
                var result = queryMachineDeviceStatusService.execute(USER_ID, MACHINE_ID);

                // Then
                assertThat(result.expectedCompletionTime()).isNull();
                assertThat(result.remainingMinutes()).isNull();
            }
        }

        @Nested
        @DisplayName("기기가 존재하지 않으면")
        class Context_machineNotFound {

            @Test
            @DisplayName("404 예외를 던지고 SmartThings를 호출하지 않는다")
            void throwsNotFound() {
                // Given
                when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
                when(machineRepository.findById(MACHINE_ID)).thenReturn(Optional.empty());

                // When & Then
                assertThatThrownBy(() -> queryMachineDeviceStatusService.execute(USER_ID, MACHINE_ID))
                        .isInstanceOf(ExpectedException.class)
                        .satisfies(e -> assertThat(((ExpectedException) e).getStatusCode())
                                .isEqualTo(HttpStatus.NOT_FOUND));
                verifyNoInteractions(deviceStatusQuerySupport);
            }
        }

        @Nested
        @DisplayName("층 제한 대상 사용자이면")
        class Context_floorRestricted {

            @Test
            @DisplayName("예외를 던지고 기기와 SmartThings를 조회하지 않는다")
            void throwsFloorRestriction() {
                // Given
                when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
                doThrow(new ExpectedException("1~4층 기숙사생이 아니라면 서비스를 이용할 수 없습니다.",
                        HttpStatus.UNAVAILABLE_FOR_LEGAL_REASONS)).when(user).validateFloorRestriction();

                // When & Then
                assertThatThrownBy(() -> queryMachineDeviceStatusService.execute(USER_ID, MACHINE_ID))
                        .isInstanceOf(ExpectedException.class);
                verifyNoInteractions(machineRepository, deviceStatusQuerySupport);
            }
        }
    }
}
