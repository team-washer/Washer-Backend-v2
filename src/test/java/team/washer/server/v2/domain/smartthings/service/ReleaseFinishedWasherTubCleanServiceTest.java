package team.washer.server.v2.domain.smartthings.service;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

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
import team.washer.server.v2.domain.smartthings.dto.response.SmartThingsDeviceStatusResDto;
import team.washer.server.v2.domain.smartthings.dto.response.SmartThingsDeviceStatusResDto.AttributeState;
import team.washer.server.v2.domain.smartthings.dto.response.SmartThingsDeviceStatusResDto.ComponentStatus;
import team.washer.server.v2.domain.smartthings.dto.response.SmartThingsDeviceStatusResDto.WasherOperatingState;
import team.washer.server.v2.domain.smartthings.service.impl.ReleaseFinishedWasherTubCleanServiceImpl;
import team.washer.server.v2.domain.smartthings.support.DeviceStatusQuerySupport;
import team.washer.server.v2.domain.smartthings.support.WasherTubCleanMachineGuard;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReleaseFinishedWasherTubCleanServiceImpl 클래스의")
class ReleaseFinishedWasherTubCleanServiceTest {

    private static final List<ReservationStatus> ACTIVE_STATUSES = List.of(ReservationStatus.RESERVED,
            ReservationStatus.RUNNING);

    @InjectMocks
    private ReleaseFinishedWasherTubCleanServiceImpl releaseService;

    @Mock
    private MachineRepository machineRepository;

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private DeviceStatusQuerySupport deviceStatusQuerySupport;

    @Mock
    private WasherTubCleanMachineGuard machineGuard;

    private Machine createMachine(LocalDateTime updatedAt) {
        var machine = Machine.builder().name("W-2F-L1").type(MachineType.WASHER).deviceId("device-1").floor(2)
                .position(Position.LEFT).number(1).status(MachineStatus.NORMAL)
                .availability(MachineAvailability.CLEANING).build();
        ReflectionTestUtils.setField(machine, "id", 1L);
        ReflectionTestUtils.setField(machine, "updatedAt", updatedAt);
        return machine;
    }

    private SmartThingsDeviceStatusResDto stoppedStatus() {
        var washerState = new WasherOperatingState(new AttributeState("stop", null, null),
                new AttributeState("finish", null, null),
                null);
        return new SmartThingsDeviceStatusResDto(Map.of("main", new ComponentStatus(washerState, null, null, null)));
    }

    @Nested
    @DisplayName("execute 메서드는")
    class Describe_execute {

        @Test
        @DisplayName("완료된 통세척 세탁기의 점유를 해제해야 한다")
        void it_releases_finished_tub_clean() {
            var machine = createMachine(LocalDateTime.now().minusMinutes(10));
            given(machineRepository.findByTypeAndAvailability(MachineType.WASHER, MachineAvailability.CLEANING))
                    .willReturn(List.of(machine));
            given(reservationRepository.findMachineIdsByStatusIn(ACTIVE_STATUSES)).willReturn(List.of());
            given(deviceStatusQuerySupport.queryAllDevicesStatus(List.of("device-1")))
                    .willReturn(Map.of("device-1", stoppedStatus()));
            given(machineGuard.releaseIfNoActiveReservation(1L)).willReturn(true);

            releaseService.execute();

            then(machineGuard).should(times(1)).releaseIfNoActiveReservation(1L);
        }

        @Test
        @DisplayName("명령 전송 직후에는 정지 상태가 조회되어도 점유를 해제하지 않아야 한다")
        void it_keeps_occupancy_during_command_start_grace_period() {
            var machine = createMachine(LocalDateTime.now());
            given(machineRepository.findByTypeAndAvailability(MachineType.WASHER, MachineAvailability.CLEANING))
                    .willReturn(List.of(machine));
            given(reservationRepository.findMachineIdsByStatusIn(ACTIVE_STATUSES)).willReturn(List.of());

            releaseService.execute();

            then(deviceStatusQuerySupport).shouldHaveNoInteractions();
            then(machineGuard).should(never()).releaseIfNoActiveReservation(1L);
        }
    }
}
