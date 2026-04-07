package team.washer.server.v2.domain.smartthings.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.*;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import team.washer.server.v2.domain.machine.entity.Machine;
import team.washer.server.v2.domain.machine.enums.MachineAvailability;
import team.washer.server.v2.domain.machine.enums.MachineStatus;
import team.washer.server.v2.domain.machine.enums.MachineType;
import team.washer.server.v2.domain.machine.enums.Position;
import team.washer.server.v2.domain.machine.repository.MachineRepository;
import team.washer.server.v2.domain.reservation.enums.ReservationStatus;
import team.washer.server.v2.domain.reservation.repository.ReservationRepository;
import team.washer.server.v2.domain.smartthings.dto.request.SmartThingsCommandReqDto;
import team.washer.server.v2.domain.smartthings.exception.SmartThingsPermissionException;
import team.washer.server.v2.domain.smartthings.service.impl.ShutdownIdleMachinesServiceImpl;

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
    private SendDeviceCommandService sendDeviceCommandService;

    private static final List<ReservationStatus> ACTIVE_STATUSES =
            List.of(ReservationStatus.RESERVED, ReservationStatus.RUNNING);

    private Machine createMachine(final String name, final String deviceId) {
        return Machine.builder().name(name).type(MachineType.WASHER).deviceId(deviceId).floor(2)
                .position(Position.LEFT).number(1).status(MachineStatus.NORMAL)
                .availability(MachineAvailability.AVAILABLE).build();
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
                then(sendDeviceCommandService).shouldHaveNoInteractions();
            }
        }

        @Nested
        @DisplayName("활성 예약이 없는 기기가 있을 때")
        class Context_with_idle_machines {

            @Test
            @DisplayName("해당 기기에 전원 종료 명령을 전송해야 한다")
            void it_sends_power_off_command() {
                // Given
                var machine = createMachine("W-2F-L1", "device-1");
                given(machineRepository.findAll()).willReturn(List.of(machine));
                given(reservationRepository.existsByMachineAndStatusIn(machine, ACTIVE_STATUSES)).willReturn(false);

                // When
                shutdownIdleMachinesService.execute();

                // Then
                then(sendDeviceCommandService).should(times(1))
                        .execute(eq("device-1"), any(SmartThingsCommandReqDto.class));
            }
        }

        @Nested
        @DisplayName("활성 예약이 있는 기기가 있을 때")
        class Context_with_reserved_machines {

            @Test
            @DisplayName("해당 기기는 건너뛰어야 한다")
            void it_skips_reserved_machine() {
                // Given
                var machine = createMachine("W-2F-L1", "device-1");
                given(machineRepository.findAll()).willReturn(List.of(machine));
                given(reservationRepository.existsByMachineAndStatusIn(machine, ACTIVE_STATUSES)).willReturn(true);

                // When
                shutdownIdleMachinesService.execute();

                // Then
                then(sendDeviceCommandService).shouldHaveNoInteractions();
            }
        }

        @Nested
        @DisplayName("SmartThings 권한 오류가 발생할 때")
        class Context_when_permission_exception_occurs {

            @Test
            @DisplayName("배치를 중단해야 한다")
            void it_stops_batch_on_permission_error() {
                // Given
                var machine1 = createMachine("W-2F-L1", "device-1");
                var machine2 = createMachine("W-2F-R1", "device-2");
                given(machineRepository.findAll()).willReturn(List.of(machine1, machine2));
                given(reservationRepository.existsByMachineAndStatusIn(machine1, ACTIVE_STATUSES)).willReturn(false);
                willThrow(new SmartThingsPermissionException("권한 없음"))
                        .given(sendDeviceCommandService).execute(eq("device-1"), any(SmartThingsCommandReqDto.class));

                // When
                shutdownIdleMachinesService.execute();

                // Then
                then(sendDeviceCommandService).should(times(1))
                        .execute(eq("device-1"), any(SmartThingsCommandReqDto.class));
                then(sendDeviceCommandService).should(never())
                        .execute(eq("device-2"), any(SmartThingsCommandReqDto.class));
            }
        }

        @Nested
        @DisplayName("일부 기기에서 일반 예외가 발생할 때")
        class Context_when_general_exception_occurs {

            @Test
            @DisplayName("해당 기기를 실패 목록에 추가하고 나머지 기기는 계속 처리해야 한다")
            void it_continues_processing_after_failure() {
                // Given
                var machine1 = createMachine("W-2F-L1", "device-1");
                var machine2 = createMachine("W-2F-R1", "device-2");
                given(machineRepository.findAll()).willReturn(List.of(machine1, machine2));
                given(reservationRepository.existsByMachineAndStatusIn(machine1, ACTIVE_STATUSES)).willReturn(false);
                given(reservationRepository.existsByMachineAndStatusIn(machine2, ACTIVE_STATUSES)).willReturn(false);
                willThrow(new RuntimeException("일시적 오류"))
                        .given(sendDeviceCommandService).execute(eq("device-1"), any(SmartThingsCommandReqDto.class));

                // When
                assertThatCode(() -> shutdownIdleMachinesService.execute()).doesNotThrowAnyException();

                // Then
                then(sendDeviceCommandService).should(times(1))
                        .execute(eq("device-2"), any(SmartThingsCommandReqDto.class));
            }
        }
    }
}
