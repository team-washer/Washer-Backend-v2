package team.washer.server.v2.domain.machine.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
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
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;

import team.themoment.sdk.exception.ExpectedException;
import team.washer.server.v2.domain.machine.entity.Machine;
import team.washer.server.v2.domain.machine.enums.MachineAvailability;
import team.washer.server.v2.domain.machine.enums.MachineStatus;
import team.washer.server.v2.domain.machine.enums.MachineType;
import team.washer.server.v2.domain.machine.enums.Position;
import team.washer.server.v2.domain.machine.repository.MachineRepository;
import team.washer.server.v2.domain.machine.service.impl.QueryAllMachinesStatusServiceImpl;
import team.washer.server.v2.domain.reservation.entity.Reservation;
import team.washer.server.v2.domain.reservation.enums.ReservationStatus;
import team.washer.server.v2.domain.reservation.repository.ReservationRepository;
import team.washer.server.v2.domain.smartthings.dto.response.SmartThingsDeviceStatusResDto;
import team.washer.server.v2.domain.smartthings.support.DeviceStatusQuerySupport;
import team.washer.server.v2.domain.smartthings.support.MachineStateDetectionSupport;
import team.washer.server.v2.domain.user.entity.User;
import team.washer.server.v2.domain.user.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class QueryAllMachinesStatusServiceTest {

    @InjectMocks
    private QueryAllMachinesStatusServiceImpl queryAllMachinesStatusService;

    @Mock
    private MachineRepository machineRepository;

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private DeviceStatusQuerySupport deviceStatusQuerySupport;

    @Mock
    private MachineStateDetectionSupport machineStateDetectionSupport;

    @Mock
    private UserRepository userRepository;

    @Mock
    private Reservation reservation;

    @Mock
    private User user;

    private static final Long USER_ID = 1L;

    private void givenUserMocked() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
    }

    @Nested
    @DisplayName("전체 기기 상태 조회")
    class ExecuteTest {

        @Test
        @DisplayName("sorted=true이면 정렬된 기기 목록과 SmartThings 상태를 성공적으로 조회한다")
        void execute_ShouldReturnSortedMachinesStatus_WhenSortedIsTrue() {
            // Given
            givenUserMocked();

            var machine1 = Machine.builder().name("W-3F-L1").type(MachineType.WASHER).deviceId("device-1").floor(3)
                    .position(Position.LEFT).number(1).status(MachineStatus.NORMAL)
                    .availability(MachineAvailability.AVAILABLE).build();

            var machine2 = Machine.builder().name("D-3F-R1").type(MachineType.DRYER).deviceId("device-2").floor(3)
                    .position(Position.RIGHT).number(1).status(MachineStatus.NORMAL)
                    .availability(MachineAvailability.IN_USE).build();

            when(machineRepository.findAll(any(Sort.class))).thenReturn(List.of(machine1, machine2));

            var washerJobAttr = new SmartThingsDeviceStatusResDto.AttributeState("run", "2026-01-26T14:00:00Z", null);
            var completionTimeAttr = new SmartThingsDeviceStatusResDto.AttributeState("2026-01-26T15:30:00Z",
                    "2026-01-26T14:30:00Z",
                    null);
            var washerOpState = new SmartThingsDeviceStatusResDto.WasherOperatingState(null,
                    washerJobAttr,
                    completionTimeAttr);
            var componentStatus = new SmartThingsDeviceStatusResDto.ComponentStatus(washerOpState, null, null, null);
            var deviceStatus = new SmartThingsDeviceStatusResDto(Map.of("main", componentStatus));

            when(deviceStatusQuerySupport.queryAllDevicesStatus(List.of("device-1", "device-2")))
                    .thenReturn(Map.of("device-1", deviceStatus, "device-2", deviceStatus));

            when(reservationRepository.findActiveReservationByMachineId(any())).thenReturn(Optional.empty());

            // When
            var result = queryAllMachinesStatusService.execute(USER_ID, true);

            // Then
            assertThat(result).hasSize(2);
            assertThat(result.getFirst().name()).isEqualTo("W-3F-L1");
            assertThat(result.getFirst().jobState()).isEqualTo("run");
            assertThat(result.getFirst().reservationId()).isNull();
        }

        @Test
        @DisplayName("sorted=false이면 정렬 없이 기기 목록과 SmartThings 상태를 조회한다")
        void execute_ShouldReturnUnsortedMachinesStatus_WhenSortedIsFalse() {
            // Given
            givenUserMocked();

            var machine1 = Machine.builder().name("W-3F-L1").type(MachineType.WASHER).deviceId("device-1").floor(3)
                    .position(Position.LEFT).number(1).status(MachineStatus.NORMAL)
                    .availability(MachineAvailability.AVAILABLE).build();

            when(machineRepository.findAll()).thenReturn(List.of(machine1));
            when(deviceStatusQuerySupport.queryAllDevicesStatus(any())).thenReturn(Map.of());
            when(reservationRepository.findActiveReservationByMachineId(any())).thenReturn(Optional.empty());

            // When
            var result = queryAllMachinesStatusService.execute(USER_ID, false);

            // Then
            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("기기가 없으면 빈 리스트를 반환한다")
        void execute_ShouldReturnEmptyList_WhenNoMachinesExist() {
            // Given
            givenUserMocked();
            when(machineRepository.findAll(any(Sort.class))).thenReturn(List.of());

            // When
            var result = queryAllMachinesStatusService.execute(USER_ID, true);

            // Then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("건조기 상태 조회 시 건조기 완료 예정 시간을 사용해야 한다")
        void execute_ShouldUseDryerCompletionTime_WhenDryerStatusContainsWasherAndDryerCapabilities() {
            // Given
            givenUserMocked();

            var machine = Machine.builder().name("D-3F-R1").type(MachineType.DRYER).deviceId("device-1").floor(3)
                    .position(Position.RIGHT).number(1).status(MachineStatus.NORMAL)
                    .availability(MachineAvailability.AVAILABLE).build();

            var washerCompletionTime = new SmartThingsDeviceStatusResDto.AttributeState("2026-01-26T15:30:00Z",
                    "2026-01-26T14:30:00Z",
                    null);
            var dryerCompletionTime = new SmartThingsDeviceStatusResDto.AttributeState("2026-01-26T16:00:00Z",
                    "2026-01-26T14:30:00Z",
                    null);
            var washerOpState = new SmartThingsDeviceStatusResDto.WasherOperatingState(null,
                    null,
                    washerCompletionTime);
            var dryerOpState = new SmartThingsDeviceStatusResDto.DryerOperatingState(null, null, dryerCompletionTime);
            var componentStatus = new SmartThingsDeviceStatusResDto.ComponentStatus(washerOpState,
                    dryerOpState,
                    null,
                    null);
            var deviceStatus = new SmartThingsDeviceStatusResDto(Map.of("main", componentStatus));

            when(machineRepository.findAll(any(Sort.class))).thenReturn(List.of(machine));
            when(deviceStatusQuerySupport.queryAllDevicesStatus(List.of("device-1")))
                    .thenReturn(Map.of("device-1", deviceStatus));
            when(reservationRepository.findActiveReservationByMachineId(any())).thenReturn(Optional.of(reservation));
            when(reservation.getStatus()).thenReturn(ReservationStatus.RUNNING);
            when(reservation.getUser()).thenReturn(user);

            // When
            var result = queryAllMachinesStatusService.execute(USER_ID, true);

            // Then
            assertThat(result.getFirst().expectedCompletionTime()).isEqualTo(LocalDateTime.of(2026, 1, 27, 1, 0));
        }

        @Test
        @DisplayName("완료 신호가 확인된 활성 예약은 완료 예정 시각과 예약 정보를 숨긴다")
        void execute_ShouldHideReservationInfo_WhenActiveReservationIsCompletedOnDeviceStatus() {
            // Given
            givenUserMocked();

            var machine = Machine.builder().name("W-3F-L1").type(MachineType.WASHER).deviceId("device-1").floor(3)
                    .position(Position.LEFT).number(1).status(MachineStatus.NORMAL)
                    .availability(MachineAvailability.AVAILABLE).build();

            var machineStateAttr = new SmartThingsDeviceStatusResDto.AttributeState("stop",
                    "2026-01-26T15:30:00Z",
                    null);
            var jobStateAttr = new SmartThingsDeviceStatusResDto.AttributeState("finish", "2026-01-26T15:30:00Z", null);
            var completionTimeAttr = new SmartThingsDeviceStatusResDto.AttributeState("2026-01-26T15:31:00Z",
                    "2026-01-26T15:30:00Z",
                    null);
            var washerOpState = new SmartThingsDeviceStatusResDto.WasherOperatingState(machineStateAttr,
                    jobStateAttr,
                    completionTimeAttr);
            var deviceStatus = new SmartThingsDeviceStatusResDto(
                    Map.of("main", new SmartThingsDeviceStatusResDto.ComponentStatus(washerOpState, null, null, null)));

            when(machineRepository.findAll(any(Sort.class))).thenReturn(List.of(machine));
            when(deviceStatusQuerySupport.queryAllDevicesStatus(List.of("device-1")))
                    .thenReturn(Map.of("device-1", deviceStatus));
            when(reservationRepository.findActiveReservationByMachineId(any())).thenReturn(Optional.of(reservation));
            when(reservation.isRunning()).thenReturn(true);
            when(machineStateDetectionSupport.isCompleted(deviceStatus, true))
                    .thenReturn(Optional.of(LocalDateTime.of(2026, 1, 27, 0, 30)));

            // When
            var result = queryAllMachinesStatusService.execute(USER_ID, true);

            // Then
            assertThat(result.getFirst().availability()).isEqualTo(MachineAvailability.AVAILABLE);
            assertThat(result.getFirst().expectedCompletionTime()).isNull();
            assertThat(result.getFirst().remainingMinutes()).isNull();
            assertThat(result.getFirst().reservationId()).isNull();
            assertThat(result.getFirst().userId()).isNull();
            assertThat(result.getFirst().roomNumber()).isNull();
        }
    }

    @Nested
    @DisplayName("층 제한 검증")
    class FloorRestrictionTest {

        @Test
        @DisplayName("5층 사용자가 조회하면 예외가 발생한다")
        void execute_ShouldThrowException_WhenUserIsOnFloor5() {
            // Given
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
            doThrow(new ExpectedException("1~4층 기숙사생이 아니라면 서비스를 이용할 수 없습니다.", HttpStatus.UNAVAILABLE_FOR_LEGAL_REASONS))
                    .when(user).validateFloorRestriction();

            // When & Then
            assertThatThrownBy(() -> queryAllMachinesStatusService.execute(USER_ID, true))
                    .isInstanceOf(ExpectedException.class).hasMessage("1~4층 기숙사생이 아니라면 서비스를 이용할 수 없습니다.")
                    .satisfies(e -> assertThat(((ExpectedException) e).getStatusCode())
                            .isEqualTo(HttpStatus.UNAVAILABLE_FOR_LEGAL_REASONS));
        }

        @Test
        @DisplayName("1~4층 사용자는 정상적으로 조회할 수 있다")
        void execute_ShouldSucceed_WhenUserIsOnFloor1To4() {
            givenUserMocked();
            when(machineRepository.findAll(any(Sort.class))).thenReturn(List.of());

            assertThatNoException().isThrownBy(() -> queryAllMachinesStatusService.execute(USER_ID, true));
        }
    }

    @Nested
    @DisplayName("기기 가용성 동적 계산")
    class ComputeAvailabilityTest {

        private Machine buildMachine(MachineAvailability availability) {
            return Machine.builder().name("W-3F-L1").type(MachineType.WASHER).deviceId("device-1").floor(3)
                    .position(Position.LEFT).number(1).status(MachineStatus.NORMAL).availability(availability).build();
        }

        private void givenMachineWithReservation(Machine machine, Reservation reservationOrNull) {
            givenUserMocked();
            when(machineRepository.findAll(any(Sort.class))).thenReturn(List.of(machine));
            when(deviceStatusQuerySupport.queryAllDevicesStatus(any())).thenReturn(Map.of());
            when(reservationRepository.findActiveReservationByMachineId(any()))
                    .thenReturn(Optional.ofNullable(reservationOrNull));
        }

        @Test
        @DisplayName("예약이 없으면 AVAILABLE을 반환한다")
        void computeAvailability_ShouldReturnAvailable_WhenNoReservation() {
            // Given
            givenMachineWithReservation(buildMachine(MachineAvailability.AVAILABLE), null);

            // When
            var result = queryAllMachinesStatusService.execute(USER_ID, true);

            // Then
            assertThat(result.getFirst().availability()).isEqualTo(MachineAvailability.AVAILABLE);
        }

        @Test
        @DisplayName("예약 상태가 RESERVED이면 RESERVED를 반환한다")
        void computeAvailability_ShouldReturnReserved_WhenReservationStatusIsReserved() {
            // Given
            when(reservation.getStatus()).thenReturn(ReservationStatus.RESERVED);
            when(reservation.getUser()).thenReturn(user);
            givenMachineWithReservation(buildMachine(MachineAvailability.AVAILABLE), reservation);

            // When
            var result = queryAllMachinesStatusService.execute(USER_ID, true);

            // Then
            assertThat(result.getFirst().availability()).isEqualTo(MachineAvailability.RESERVED);
        }

        @Test
        @DisplayName("예약 상태가 RUNNING이면 IN_USE를 반환한다")
        void computeAvailability_ShouldReturnInUse_WhenReservationStatusIsRunning() {
            // Given
            when(reservation.getStatus()).thenReturn(ReservationStatus.RUNNING);
            when(reservation.getUser()).thenReturn(user);
            givenMachineWithReservation(buildMachine(MachineAvailability.AVAILABLE), reservation);

            // When
            var result = queryAllMachinesStatusService.execute(USER_ID, true);

            // Then
            assertThat(result.getFirst().availability()).isEqualTo(MachineAvailability.IN_USE);
        }

        @Test
        @DisplayName("기기가 UNAVAILABLE이면 예약과 무관하게 UNAVAILABLE을 반환한다")
        void computeAvailability_ShouldReturnUnavailable_WhenMachineIsUnavailable() {
            // Given
            when(reservation.getUser()).thenReturn(user);
            givenMachineWithReservation(buildMachine(MachineAvailability.UNAVAILABLE), reservation);

            // When
            var result = queryAllMachinesStatusService.execute(USER_ID, true);

            // Then
            assertThat(result.getFirst().availability()).isEqualTo(MachineAvailability.UNAVAILABLE);
        }
    }
}
