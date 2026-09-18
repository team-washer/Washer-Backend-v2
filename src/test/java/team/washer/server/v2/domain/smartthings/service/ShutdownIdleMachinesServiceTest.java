package team.washer.server.v2.domain.smartthings.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.*;

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
import org.springframework.test.util.ReflectionTestUtils;

import team.washer.server.v2.domain.machine.entity.Machine;
import team.washer.server.v2.domain.machine.enums.MachineAvailability;
import team.washer.server.v2.domain.machine.enums.MachineStatus;
import team.washer.server.v2.domain.machine.enums.MachineType;
import team.washer.server.v2.domain.machine.enums.Position;
import team.washer.server.v2.domain.machine.repository.MachineRepository;
import team.washer.server.v2.domain.reservation.entity.Reservation;
import team.washer.server.v2.domain.reservation.enums.ReservationStatus;
import team.washer.server.v2.domain.reservation.repository.ReservationRepository;
import team.washer.server.v2.domain.smartthings.dto.response.SmartThingsDeviceStatusResDto;
import team.washer.server.v2.domain.smartthings.exception.SmartThingsPermissionException;
import team.washer.server.v2.domain.smartthings.service.impl.ShutdownIdleMachinesServiceImpl;
import team.washer.server.v2.domain.smartthings.support.DeviceShutdownSupport;
import team.washer.server.v2.domain.smartthings.support.DeviceShutdownSupport.ShutdownResult;
import team.washer.server.v2.domain.smartthings.support.DeviceStatusQuerySupport;
import team.washer.server.v2.global.util.DateTimeUtil;

@ExtendWith(MockitoExtension.class)
@DisplayName("ShutdownIdleMachinesServiceImpl 클래스의")
class ShutdownIdleMachinesServiceTest {

    @InjectMocks
    private ShutdownIdleMachinesServiceImpl shutdownIdleMachinesService;

    @Mock
    private MachineRepository machineRepository;

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private DeviceStatusQuerySupport deviceStatusQuerySupport;

    @Mock
    private DeviceShutdownSupport deviceShutdownSupport;

    private static final SmartThingsDeviceStatusResDto EMPTY_STATUS = new SmartThingsDeviceStatusResDto(Map.of());

    private Machine createMachine(final Long id, final String name, final String deviceId) {
        return createMachine(id, name, deviceId, MachineType.WASHER, MachineAvailability.AVAILABLE);
    }

    private Machine createMachine(final Long id,
            final String name,
            final String deviceId,
            final MachineType type,
            final MachineAvailability availability) {
        var machine = Machine.builder().name(name).type(type).deviceId(deviceId).floor(2).position(Position.LEFT)
                .number(1).status(MachineStatus.NORMAL).availability(availability).build();
        ReflectionTestUtils.setField(machine, "id", id);
        return machine;
    }

    private Reservation completedReservation(final LocalDateTime expectedCompletionTime,
            final LocalDateTime actualCompletionTime) {
        return Reservation.builder().status(ReservationStatus.COMPLETED).expectedCompletionTime(expectedCompletionTime)
                .actualCompletionTime(actualCompletionTime).build();
    }

    private void givenSingleIdleMachine(final Machine machine) {
        given(machineRepository.findAll()).willReturn(List.of(machine));
        given(reservationRepository.findCurrentlyActiveMachineIds()).willReturn(List.of());
        given(deviceStatusQuerySupport.queryAllDevicesStatus(List.of(machine.getDeviceId())))
                .willReturn(Map.of(machine.getDeviceId(), EMPTY_STATUS));
    }

    @Nested
    @DisplayName("execute 메서드는")
    class Describe_execute {

        @Nested
        @DisplayName("기기가 없을 때")
        class Context_with_no_machines {

            @Test
            @DisplayName("아무것도 하지 않아야 한다")
            void it_does_nothing() {
                // Given
                given(machineRepository.findAll()).willReturn(List.of());

                // When
                shutdownIdleMachinesService.execute();

                // Then
                then(reservationRepository).shouldHaveNoInteractions();
                then(deviceShutdownSupport).shouldHaveNoInteractions();
            }
        }

        @Nested
        @DisplayName("활성 예약이 없는 기기가 있을 때")
        class Context_with_idle_machines {

            @Test
            @DisplayName("기기 상태를 조회하여 전원 차단을 위임해야 한다")
            void it_delegates_safe_shutdown() {
                // Given
                var machine = createMachine(1L, "W-2F-L1", "device-1");
                given(machineRepository.findAll()).willReturn(List.of(machine));
                given(reservationRepository.findCurrentlyActiveMachineIds()).willReturn(List.of());
                given(deviceStatusQuerySupport.queryAllDevicesStatus(List.of("device-1")))
                        .willReturn(Map.of("device-1", EMPTY_STATUS));
                given(deviceShutdownSupport.shutdown(eq(machine), any())).willReturn(ShutdownResult.POWERED_OFF);

                // When
                shutdownIdleMachinesService.execute();

                // Then
                then(deviceShutdownSupport).should(times(1)).shutdown(eq(machine), any());
            }
        }

        @Nested
        @DisplayName("활성 예약이 있는 기기가 있을 때")
        class Context_with_reserved_machines {

            @Test
            @DisplayName("해당 기기는 건너뛰고 상태 조회와 종료를 하지 않아야 한다")
            void it_skips_reserved_machine() {
                // Given
                var machine = createMachine(1L, "W-2F-L1", "device-1");
                given(machineRepository.findAll()).willReturn(List.of(machine));
                given(reservationRepository.findCurrentlyActiveMachineIds()).willReturn(List.of(1L));

                // When
                shutdownIdleMachinesService.execute();

                // Then
                then(deviceStatusQuerySupport).shouldHaveNoInteractions();
                then(deviceShutdownSupport).shouldHaveNoInteractions();
            }
        }

        @Nested
        @DisplayName("통세척 중인 기기가 있을 때")
        class Context_with_cleaning_machine {

            @Test
            @DisplayName("예약이 없어도 상태 조회와 전원 차단을 하지 않아야 한다")
            void it_skips_cleaning_machine() {
                // Given
                var machine = createMachine(1L,
                        "W-2F-L1",
                        "device-1",
                        MachineType.WASHER,
                        MachineAvailability.CLEANING);
                given(machineRepository.findAll()).willReturn(List.of(machine));
                given(reservationRepository.findCurrentlyActiveMachineIds()).willReturn(List.of());

                // When
                shutdownIdleMachinesService.execute();

                // Then
                then(deviceStatusQuerySupport).shouldHaveNoInteractions();
                then(deviceShutdownSupport).shouldHaveNoInteractions();
            }
        }

        @Nested
        @DisplayName("예약 없이 작동 중인 건조기일 때")
        class Context_with_operating_dryer {

            @Test
            @DisplayName("배수 유예 없이 바로 전원을 차단해야 한다")
            void it_powers_off_immediately() {
                // Given
                var machine = createMachine(1L,
                        "D-2F-L1",
                        "device-1",
                        MachineType.DRYER,
                        MachineAvailability.AVAILABLE);
                givenSingleIdleMachine(machine);
                given(deviceShutdownSupport.isOperating(EMPTY_STATUS, false)).willReturn(true);
                given(deviceShutdownSupport.shutdown(machine, EMPTY_STATUS)).willReturn(ShutdownResult.POWERED_OFF);

                // When
                shutdownIdleMachinesService.execute();

                // Then
                then(deviceShutdownSupport).should(times(1)).shutdown(machine, EMPTY_STATUS);
                then(reservationRepository).should(never())
                        .findFirstByMachineIdAndStatusOrderByActualCompletionTimeDesc(any(), any());
            }
        }

        @Nested
        @DisplayName("예약 없이 작동 중인 세탁기일 때")
        class Context_with_operating_washer {

            @Test
            @DisplayName("마지막 완료 예약의 완료 예정 시각 + 5분이 지나지 않았으면 전원을 차단하지 않아야 한다")
            void it_skips_within_drain_grace() {
                // Given
                var machine = createMachine(1L, "W-2F-L1", "device-1");
                givenSingleIdleMachine(machine);
                given(deviceShutdownSupport.isOperating(EMPTY_STATUS, true)).willReturn(true);
                var now = DateTimeUtil.nowInKorea();
                given(reservationRepository.findFirstByMachineIdAndStatusOrderByActualCompletionTimeDesc(1L,
                        ReservationStatus.COMPLETED))
                        .willReturn(Optional.of(completedReservation(now.minusMinutes(3), now.minusMinutes(6))));

                // When
                shutdownIdleMachinesService.execute();

                // Then
                then(deviceShutdownSupport).should(never()).shutdown(any(), any());
            }

            @Test
            @DisplayName("완료 예정 시각 + 5분이 지났으면 전원을 차단해야 한다")
            void it_powers_off_after_drain_grace() {
                // Given
                var machine = createMachine(1L, "W-2F-L1", "device-1");
                givenSingleIdleMachine(machine);
                given(deviceShutdownSupport.isOperating(EMPTY_STATUS, true)).willReturn(true);
                var now = DateTimeUtil.nowInKorea();
                given(reservationRepository.findFirstByMachineIdAndStatusOrderByActualCompletionTimeDesc(1L,
                        ReservationStatus.COMPLETED))
                        .willReturn(Optional.of(completedReservation(now.minusMinutes(6), now.minusMinutes(1))));
                given(deviceShutdownSupport.shutdown(machine, EMPTY_STATUS)).willReturn(ShutdownResult.POWERED_OFF);

                // When
                shutdownIdleMachinesService.execute();

                // Then
                then(deviceShutdownSupport).should(times(1)).shutdown(machine, EMPTY_STATUS);
            }

            @Test
            @DisplayName("완료 예정 시각이 없으면 실제 완료 시각 + 5분을 기준으로 유예해야 한다")
            void it_uses_actual_completion_time_when_expected_missing() {
                // Given
                var machine = createMachine(1L, "W-2F-L1", "device-1");
                givenSingleIdleMachine(machine);
                given(deviceShutdownSupport.isOperating(EMPTY_STATUS, true)).willReturn(true);
                given(reservationRepository.findFirstByMachineIdAndStatusOrderByActualCompletionTimeDesc(1L,
                        ReservationStatus.COMPLETED))
                        .willReturn(Optional.of(completedReservation(null, DateTimeUtil.nowInKorea().minusMinutes(2))));

                // When
                shutdownIdleMachinesService.execute();

                // Then
                then(deviceShutdownSupport).should(never()).shutdown(any(), any());
            }

            @Test
            @DisplayName("완료 예약 이력이 없으면 바로 전원을 차단해야 한다")
            void it_powers_off_without_completed_reservation() {
                // Given
                var machine = createMachine(1L, "W-2F-L1", "device-1");
                givenSingleIdleMachine(machine);
                given(deviceShutdownSupport.isOperating(EMPTY_STATUS, true)).willReturn(true);
                given(reservationRepository.findFirstByMachineIdAndStatusOrderByActualCompletionTimeDesc(1L,
                        ReservationStatus.COMPLETED)).willReturn(Optional.empty());
                given(deviceShutdownSupport.shutdown(machine, EMPTY_STATUS)).willReturn(ShutdownResult.POWERED_OFF);

                // When
                shutdownIdleMachinesService.execute();

                // Then
                then(deviceShutdownSupport).should(times(1)).shutdown(machine, EMPTY_STATUS);
            }
        }

        @Nested
        @DisplayName("SmartThings 권한 오류가 발생할 때")
        class Context_when_permission_exception_occurs {

            @Test
            @DisplayName("배치를 중단해야 한다")
            void it_stops_batch_on_permission_error() {
                // Given
                var machine1 = createMachine(1L, "W-2F-L1", "device-1");
                var machine2 = createMachine(2L, "W-2F-R1", "device-2");
                given(machineRepository.findAll()).willReturn(List.of(machine1, machine2));
                given(reservationRepository.findCurrentlyActiveMachineIds()).willReturn(List.of());
                given(deviceStatusQuerySupport.queryAllDevicesStatus(List.of("device-1", "device-2")))
                        .willReturn(Map.of("device-1", EMPTY_STATUS, "device-2", EMPTY_STATUS));
                willThrow(new SmartThingsPermissionException("권한 없음")).given(deviceShutdownSupport)
                        .shutdown(eq(machine1), any());

                // When
                shutdownIdleMachinesService.execute();

                // Then
                then(deviceShutdownSupport).should(times(1)).shutdown(eq(machine1), any());
                then(deviceShutdownSupport).should(never()).shutdown(eq(machine2), any());
            }
        }

        @Nested
        @DisplayName("일부 기기에서 일반 예외가 발생할 때")
        class Context_when_general_exception_occurs {

            @Test
            @DisplayName("해당 기기를 실패 목록에 추가하고 나머지 기기는 계속 처리해야 한다")
            void it_continues_processing_after_failure() {
                // Given
                var machine1 = createMachine(1L, "W-2F-L1", "device-1");
                var machine2 = createMachine(2L, "W-2F-R1", "device-2");
                given(machineRepository.findAll()).willReturn(List.of(machine1, machine2));
                given(reservationRepository.findCurrentlyActiveMachineIds()).willReturn(List.of());
                given(deviceStatusQuerySupport.queryAllDevicesStatus(List.of("device-1", "device-2")))
                        .willReturn(Map.of("device-1", EMPTY_STATUS, "device-2", EMPTY_STATUS));
                willThrow(new RuntimeException("일시적 오류")).given(deviceShutdownSupport).shutdown(eq(machine1), any());
                given(deviceShutdownSupport.shutdown(eq(machine2), any())).willReturn(ShutdownResult.POWERED_OFF);

                // When
                assertThatCode(() -> shutdownIdleMachinesService.execute()).doesNotThrowAnyException();

                // Then
                then(deviceShutdownSupport).should(times(1)).shutdown(eq(machine2), any());
            }
        }
    }
}
