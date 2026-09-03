package team.washer.server.v2.domain.smartthings.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
import team.washer.server.v2.domain.smartthings.dto.request.SmartThingsCommandReqDto;
import team.washer.server.v2.domain.smartthings.dto.response.SmartThingsDeviceStatusResDto;
import team.washer.server.v2.domain.smartthings.dto.response.SmartThingsDeviceStatusResDto.AttributeState;
import team.washer.server.v2.domain.smartthings.dto.response.SmartThingsDeviceStatusResDto.ComponentStatus;
import team.washer.server.v2.domain.smartthings.dto.response.SmartThingsDeviceStatusResDto.RemoteControlStatus;
import team.washer.server.v2.domain.smartthings.dto.response.SmartThingsDeviceStatusResDto.SwitchCapability;
import team.washer.server.v2.domain.smartthings.dto.response.SmartThingsDeviceStatusResDto.WasherOperatingState;
import team.washer.server.v2.domain.smartthings.service.impl.RunWasherTubCleanServiceImpl;
import team.washer.server.v2.domain.smartthings.support.DeviceStatusQuerySupport;
import team.washer.server.v2.domain.smartthings.support.WasherTubCleanMachineGuard;
import team.washer.server.v2.domain.smartthings.support.WasherTubCleanMachineGuard.TubCleanTarget;
import team.washer.server.v2.global.thirdparty.smartthings.config.SmartThingsTubCleanEnvironment;

@ExtendWith(MockitoExtension.class)
@DisplayName("RunWasherTubCleanServiceImpl 클래스의")
class RunWasherTubCleanServiceTest {

    private static final List<ReservationStatus> ACTIVE_STATUSES = List.of(ReservationStatus.RESERVED,
            ReservationStatus.RUNNING);

    @InjectMocks
    private RunWasherTubCleanServiceImpl runWasherTubCleanService;

    @Mock
    private MachineRepository machineRepository;

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private DeviceStatusQuerySupport deviceStatusQuerySupport;

    @Mock
    private SendDeviceCommandService sendDeviceCommandService;

    @Mock
    private WasherTubCleanMachineGuard machineGuard;

    @Mock
    private SmartThingsTubCleanEnvironment tubCleanEnvironment;

    private Machine createMachine() {
        var machine = Machine.builder().name("W-2F-L1").type(MachineType.WASHER).deviceId("device-1").floor(2)
                .position(Position.LEFT).number(1).status(MachineStatus.NORMAL)
                .availability(MachineAvailability.AVAILABLE).build();
        ReflectionTestUtils.setField(machine, "id", 1L);
        return machine;
    }

    private SmartThingsDeviceStatusResDto idleStatus(boolean remoteEnabled) {
        var washerState = new WasherOperatingState(new AttributeState("stop", null, null),
                new AttributeState("none", null, null),
                null);
        var remoteControl = new RemoteControlStatus(new AttributeState(String.valueOf(remoteEnabled), null, null));
        var switchCapability = new SwitchCapability(new AttributeState("on", null, null));
        return new SmartThingsDeviceStatusResDto(
                Map.of("main", new ComponentStatus(washerState, null, switchCapability, remoteControl)));
    }

    @Nested
    @DisplayName("execute 메서드는")
    class Describe_execute {

        @Test
        @DisplayName("통세척 코스가 설정되지 않으면 기기를 조회하지 않아야 한다")
        void it_skips_when_cycle_is_not_configured() {
            given(tubCleanEnvironment.hasCycle()).willReturn(false);

            runWasherTubCleanService.execute();

            then(machineRepository).shouldHaveNoInteractions();
            then(sendDeviceCommandService).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("유휴 세탁기를 점유하고 통세척 코스와 실행 명령을 전송해야 한다")
        void it_starts_tub_clean_on_idle_washer() {
            var machine = createMachine();
            given(tubCleanEnvironment.hasCycle()).willReturn(true);
            given(tubCleanEnvironment.cycle()).willReturn("Course_82");
            given(machineRepository.findByTypeAndStatusAndAvailability(MachineType.WASHER,
                    MachineStatus.NORMAL,
                    MachineAvailability.AVAILABLE)).willReturn(List.of(machine));
            given(reservationRepository.findMachineIdsByStatusIn(ACTIVE_STATUSES)).willReturn(List.of());
            given(deviceStatusQuerySupport.queryAllDevicesStatus(List.of("device-1")))
                    .willReturn(Map.of("device-1", idleStatus(true)));
            given(machineGuard.occupyIfAvailable(1L))
                    .willReturn(Optional.of(new TubCleanTarget(1L, "W-2F-L1", "device-1")));

            runWasherTubCleanService.execute();

            var commandCaptor = ArgumentCaptor.forClass(SmartThingsCommandReqDto.class);
            then(sendDeviceCommandService).should(times(1)).execute(eq("device-1"), commandCaptor.capture());
            assertThat(commandCaptor.getValue().commands()).hasSize(2);
            assertThat(commandCaptor.getValue().commands().get(0).capability()).isEqualTo("samsungce.washerCycle");
            assertThat(commandCaptor.getValue().commands().get(0).arguments()).containsExactly("Course_82");
            assertThat(commandCaptor.getValue().commands().get(1).command()).isEqualTo("setMachineState");
        }

        @Test
        @DisplayName("활성 예약이 있는 세탁기는 상태 조회와 실행에서 제외해야 한다")
        void it_skips_reserved_washer() {
            var machine = createMachine();
            given(tubCleanEnvironment.hasCycle()).willReturn(true);
            given(machineRepository.findByTypeAndStatusAndAvailability(MachineType.WASHER,
                    MachineStatus.NORMAL,
                    MachineAvailability.AVAILABLE)).willReturn(List.of(machine));
            given(reservationRepository.findMachineIdsByStatusIn(ACTIVE_STATUSES)).willReturn(List.of(1L));

            runWasherTubCleanService.execute();

            then(deviceStatusQuerySupport).shouldHaveNoInteractions();
            then(sendDeviceCommandService).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("원격 제어가 꺼진 세탁기는 실행하지 않아야 한다")
        void it_skips_washer_with_remote_control_disabled() {
            var machine = createMachine();
            given(tubCleanEnvironment.hasCycle()).willReturn(true);
            given(machineRepository.findByTypeAndStatusAndAvailability(MachineType.WASHER,
                    MachineStatus.NORMAL,
                    MachineAvailability.AVAILABLE)).willReturn(List.of(machine));
            given(reservationRepository.findMachineIdsByStatusIn(ACTIVE_STATUSES)).willReturn(List.of());
            given(deviceStatusQuerySupport.queryAllDevicesStatus(List.of("device-1")))
                    .willReturn(Map.of("device-1", idleStatus(false)));

            runWasherTubCleanService.execute();

            then(machineGuard).should(never()).occupyIfAvailable(any());
            then(sendDeviceCommandService).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("명령 전송에 실패하면 세탁기 점유를 해제해야 한다")
        void it_releases_occupancy_when_command_fails() {
            var machine = createMachine();
            given(tubCleanEnvironment.hasCycle()).willReturn(true);
            given(tubCleanEnvironment.cycle()).willReturn("Course_82");
            given(machineRepository.findByTypeAndStatusAndAvailability(MachineType.WASHER,
                    MachineStatus.NORMAL,
                    MachineAvailability.AVAILABLE)).willReturn(List.of(machine));
            given(reservationRepository.findMachineIdsByStatusIn(ACTIVE_STATUSES)).willReturn(List.of());
            given(deviceStatusQuerySupport.queryAllDevicesStatus(List.of("device-1")))
                    .willReturn(Map.of("device-1", idleStatus(true)));
            given(machineGuard.occupyIfAvailable(1L))
                    .willReturn(Optional.of(new TubCleanTarget(1L, "W-2F-L1", "device-1")));
            willThrow(new RuntimeException("명령 전송 실패")).given(sendDeviceCommandService).execute(eq("device-1"), any());

            runWasherTubCleanService.execute();

            then(machineGuard).should(times(1)).releaseIfNoActiveReservation(1L);
        }
    }
}
