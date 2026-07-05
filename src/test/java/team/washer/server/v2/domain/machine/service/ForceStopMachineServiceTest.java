package team.washer.server.v2.domain.machine.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;
import static org.mockito.Mockito.lenient;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import team.themoment.sdk.exception.ExpectedException;
import team.washer.server.v2.domain.machine.entity.Machine;
import team.washer.server.v2.domain.machine.enums.ForceStopResult;
import team.washer.server.v2.domain.machine.enums.MachineAvailability;
import team.washer.server.v2.domain.machine.enums.MachineStatus;
import team.washer.server.v2.domain.machine.enums.MachineType;
import team.washer.server.v2.domain.machine.enums.Position;
import team.washer.server.v2.domain.machine.repository.MachineRepository;
import team.washer.server.v2.domain.machine.service.impl.ForceStopMachineServiceImpl;
import team.washer.server.v2.domain.reservation.entity.Reservation;
import team.washer.server.v2.domain.reservation.enums.ReservationStatus;
import team.washer.server.v2.domain.reservation.repository.ReservationRepository;
import team.washer.server.v2.domain.smartthings.dto.request.SmartThingsCommandReqDto;
import team.washer.server.v2.domain.smartthings.dto.response.SmartThingsDeviceStatusResDto;
import team.washer.server.v2.domain.smartthings.dto.response.SmartThingsDeviceStatusResDto.AttributeState;
import team.washer.server.v2.domain.smartthings.dto.response.SmartThingsDeviceStatusResDto.ComponentStatus;
import team.washer.server.v2.domain.smartthings.dto.response.SmartThingsDeviceStatusResDto.DryerOperatingState;
import team.washer.server.v2.domain.smartthings.dto.response.SmartThingsDeviceStatusResDto.SwitchCapability;
import team.washer.server.v2.domain.smartthings.dto.response.SmartThingsDeviceStatusResDto.WasherOperatingState;
import team.washer.server.v2.domain.smartthings.service.SendDeviceCommandService;
import team.washer.server.v2.domain.smartthings.support.DeviceStatusQuerySupport;
import team.washer.server.v2.domain.user.entity.User;

@ExtendWith(MockitoExtension.class)
@DisplayName("ForceStopMachineServiceImpl 클래스의")
class ForceStopMachineServiceTest {

    @InjectMocks
    private ForceStopMachineServiceImpl forceStopMachineService;

    @Mock
    private MachineRepository machineRepository;

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private DeviceStatusQuerySupport deviceStatusQuerySupport;

    @Mock
    private SendDeviceCommandService sendDeviceCommandService;

    @Mock
    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void setUp() {
        lenient().when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        });
    }

    private Machine createMachine(MachineType type, MachineStatus status, MachineAvailability availability) {
        return Machine.builder().name("W-2F-L1").type(type).deviceId("device-1").floor(2).position(Position.LEFT)
                .number(1).status(status).availability(availability).build();
    }

    private Reservation createReservation(Machine machine, ReservationStatus status) {
        var user = User.builder().name("김철수").studentId("20210001").roomNumber("301").grade(3).floor(3).penaltyCount(0)
                .build();
        return Reservation.builder().user(user).machine(machine).reservedAt(LocalDateTime.now())
                .startTime(LocalDateTime.now()).status(status).build();
    }

    private SmartThingsDeviceStatusResDto washerStatus(String machineState) {
        var washerOperatingState = new WasherOperatingState(new AttributeState(machineState, null, null), null, null);
        return new SmartThingsDeviceStatusResDto(
                Map.of("main", new ComponentStatus(washerOperatingState, null, null, null)));
    }

    private SmartThingsDeviceStatusResDto dryerStatus(String machineState) {
        var dryerOperatingState = new DryerOperatingState(new AttributeState(machineState, null, null), null, null);
        return new SmartThingsDeviceStatusResDto(
                Map.of("main", new ComponentStatus(null, dryerOperatingState, null, null)));
    }

    private SmartThingsDeviceStatusResDto powerOffStatus() {
        var switchCapability = new SwitchCapability(new AttributeState("off", null, null));
        return new SmartThingsDeviceStatusResDto(
                Map.of("main", new ComponentStatus(null, null, switchCapability, null)));
    }

    private SmartThingsDeviceStatusResDto unknownStatus() {
        return new SmartThingsDeviceStatusResDto(Map.of("main", new ComponentStatus(null, null, null, null)));
    }

    private void setId(Object entity, Long id) {
        try {
            var idField = entity.getClass().getSuperclass().getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(entity, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Nested
    @DisplayName("execute 메서드는")
    class Describe_execute {

        @Nested
        @DisplayName("동작 중인 세탁기에 활성 예약이 있으면")
        class Context_with_running_washer_and_active_reservation {

            @Test
            @DisplayName("세탁기 정지 명령만 보내고 예약을 패널티 없이 취소한다")
            void it_stops_washer_and_cancels_active_reservation() {
                // Given
                var machineId = 1L;
                var reservationId = 10L;
                var machine = createMachine(MachineType.WASHER, MachineStatus.NORMAL, MachineAvailability.IN_USE);
                var reservation = createReservation(machine, ReservationStatus.RUNNING);
                var status = washerStatus("run");
                setId(machine, machineId);
                setId(reservation, reservationId);

                given(machineRepository.findById(machineId)).willReturn(Optional.of(machine));
                given(machineRepository.findByIdForUpdate(machineId)).willReturn(Optional.of(machine));
                given(deviceStatusQuerySupport.queryDeviceStatus("device-1")).willReturn(status);
                given(reservationRepository.findFirstActiveReservationByMachineId(machineId,
                        List.of(ReservationStatus.RESERVED, ReservationStatus.RUNNING)))
                        .willReturn(List.of(reservation));
                given(reservationRepository.save(any(Reservation.class)))
                        .willAnswer(invocation -> invocation.getArgument(0));
                given(machineRepository.save(any(Machine.class))).willAnswer(invocation -> invocation.getArgument(0));

                // When
                var result = forceStopMachineService.execute(machineId);

                // Then
                assertThat(result.machineId()).isEqualTo(machineId);
                assertThat(result.forceStopResult()).isEqualTo(ForceStopResult.STOPPED);
                assertThat(result.previousMachineState()).isEqualTo("run");
                assertThat(result.cancelledReservationId()).isEqualTo(reservationId);
                assertThat(result.reservationCancelled()).isTrue();
                assertThat(result.availability()).isEqualTo(MachineAvailability.AVAILABLE);
                assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CANCELLED);

                var commandCaptor = ArgumentCaptor.forClass(SmartThingsCommandReqDto.class);
                then(sendDeviceCommandService).should(times(1)).execute(eq("device-1"), commandCaptor.capture());
                var command = commandCaptor.getValue().commands().get(0);
                assertThat(command.capability()).isEqualTo("washerOperatingState");
                assertThat(command.command()).isEqualTo("setMachineState");
                assertThat(command.arguments()).containsExactly("stop");
                then(reservationRepository).should(times(1)).save(reservation);
                then(machineRepository).should(times(1)).save(machine);
            }
        }

        @Nested
        @DisplayName("RUNNING 예약과 RESERVED 예약이 동시에 조회되면")
        class Context_with_running_and_reserved_reservations {

            @Test
            @DisplayName("조회 순서와 무관하게 RUNNING 예약을 우선 취소한다")
            void it_prioritizes_running_reservation() {
                // Given
                var machineId = 1L;
                var runningReservationId = 10L;
                var reservedReservationId = 11L;
                var machine = createMachine(MachineType.WASHER, MachineStatus.NORMAL, MachineAvailability.IN_USE);
                var runningReservation = createReservation(machine, ReservationStatus.RUNNING);
                var reservedReservation = createReservation(machine, ReservationStatus.RESERVED);
                var status = washerStatus("run");
                setId(machine, machineId);
                setId(runningReservation, runningReservationId);
                setId(reservedReservation, reservedReservationId);

                given(machineRepository.findById(machineId)).willReturn(Optional.of(machine));
                given(machineRepository.findByIdForUpdate(machineId)).willReturn(Optional.of(machine));
                given(deviceStatusQuerySupport.queryDeviceStatus("device-1")).willReturn(status);
                given(reservationRepository.findFirstActiveReservationByMachineId(machineId,
                        List.of(ReservationStatus.RESERVED, ReservationStatus.RUNNING)))
                        .willReturn(List.of(reservedReservation, runningReservation));
                given(reservationRepository.save(any(Reservation.class)))
                        .willAnswer(invocation -> invocation.getArgument(0));
                given(machineRepository.save(any(Machine.class))).willAnswer(invocation -> invocation.getArgument(0));

                // When
                var result = forceStopMachineService.execute(machineId);

                // Then
                assertThat(result.cancelledReservationId()).isEqualTo(runningReservationId);
                assertThat(result.reservationCancelled()).isTrue();
                assertThat(runningReservation.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
                assertThat(reservedReservation.getStatus()).isEqualTo(ReservationStatus.RESERVED);
                then(reservationRepository).should(times(1)).save(runningReservation);
                then(reservationRepository).should(never()).save(reservedReservation);
            }
        }

        @Nested
        @DisplayName("일시정지 중인 건조기이면")
        class Context_with_paused_dryer {

            @Test
            @DisplayName("건조기 정지 명령을 보낸다")
            void it_sends_dryer_stop_command() {
                // Given
                var machineId = 1L;
                var machine = createMachine(MachineType.DRYER, MachineStatus.NORMAL, MachineAvailability.IN_USE);
                var status = dryerStatus("pause");
                setId(machine, machineId);

                given(machineRepository.findById(machineId)).willReturn(Optional.of(machine));
                given(machineRepository.findByIdForUpdate(machineId)).willReturn(Optional.of(machine));
                given(deviceStatusQuerySupport.queryDeviceStatus("device-1")).willReturn(status);
                given(reservationRepository.findFirstActiveReservationByMachineId(machineId,
                        List.of(ReservationStatus.RESERVED, ReservationStatus.RUNNING))).willReturn(List.of());
                given(machineRepository.save(any(Machine.class))).willAnswer(invocation -> invocation.getArgument(0));

                // When
                var result = forceStopMachineService.execute(machineId);

                // Then
                assertThat(result.forceStopResult()).isEqualTo(ForceStopResult.STOPPED);
                var commandCaptor = ArgumentCaptor.forClass(SmartThingsCommandReqDto.class);
                then(sendDeviceCommandService).should(times(1)).execute(eq("device-1"), commandCaptor.capture());
                assertThat(commandCaptor.getValue().commands().get(0).capability()).isEqualTo("dryerOperatingState");
            }
        }

        @Nested
        @DisplayName("예약 상태이고 이미 정지된 기기이면")
        class Context_with_reserved_and_already_stopped_machine {

            @Test
            @DisplayName("명령과 예약 취소 없이 예약 상태를 유지한다")
            void it_keeps_reserved_state_without_command() {
                // Given
                var machineId = 1L;
                var machine = createMachine(MachineType.WASHER, MachineStatus.NORMAL, MachineAvailability.RESERVED);
                var reservation = createReservation(machine, ReservationStatus.RESERVED);
                var status = washerStatus("stop");
                setId(machine, machineId);
                setId(reservation, 10L);

                given(machineRepository.findById(machineId)).willReturn(Optional.of(machine));
                given(machineRepository.findByIdForUpdate(machineId)).willReturn(Optional.of(machine));
                given(deviceStatusQuerySupport.queryDeviceStatus("device-1")).willReturn(status);
                given(reservationRepository.findFirstActiveReservationByMachineId(machineId,
                        List.of(ReservationStatus.RESERVED, ReservationStatus.RUNNING)))
                        .willReturn(List.of(reservation));
                given(machineRepository.save(any(Machine.class))).willAnswer(invocation -> invocation.getArgument(0));

                // When
                var result = forceStopMachineService.execute(machineId);

                // Then
                assertThat(result.forceStopResult()).isEqualTo(ForceStopResult.ALREADY_STOPPED);
                assertThat(result.cancelledReservationId()).isNull();
                assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.RESERVED);
                assertThat(machine.getAvailability()).isEqualTo(MachineAvailability.RESERVED);
                then(sendDeviceCommandService).shouldHaveNoInteractions();
                then(reservationRepository).should(never()).save(any(Reservation.class));
            }
        }

        @Nested
        @DisplayName("전원이 꺼진 기기이면")
        class Context_with_powered_off_machine {

            @Test
            @DisplayName("전원 차단 명령을 보내지 않고 이미 정지 상태로 처리한다")
            void it_does_not_send_power_off_command() {
                // Given
                var machineId = 1L;
                var machine = createMachine(MachineType.WASHER, MachineStatus.NORMAL, MachineAvailability.AVAILABLE);
                setId(machine, machineId);

                given(machineRepository.findById(machineId)).willReturn(Optional.of(machine));
                given(machineRepository.findByIdForUpdate(machineId)).willReturn(Optional.of(machine));
                given(deviceStatusQuerySupport.queryDeviceStatus("device-1")).willReturn(powerOffStatus());
                given(reservationRepository.findFirstActiveReservationByMachineId(machineId,
                        List.of(ReservationStatus.RESERVED, ReservationStatus.RUNNING))).willReturn(List.of());
                given(machineRepository.save(any(Machine.class))).willAnswer(invocation -> invocation.getArgument(0));

                // When
                var result = forceStopMachineService.execute(machineId);

                // Then
                assertThat(result.forceStopResult()).isEqualTo(ForceStopResult.ALREADY_STOPPED);
                then(sendDeviceCommandService).shouldHaveNoInteractions();
            }
        }

        @Nested
        @DisplayName("기기 동작 상태를 확인할 수 없으면")
        class Context_with_unknown_machine_state {

            @Test
            @DisplayName("명령 전송과 DB 변경 없이 예외를 던진다")
            void it_throws_without_command_or_database_update() {
                // Given
                var machineId = 1L;
                var machine = createMachine(MachineType.WASHER, MachineStatus.NORMAL, MachineAvailability.IN_USE);
                setId(machine, machineId);

                given(machineRepository.findById(machineId)).willReturn(Optional.of(machine));
                given(deviceStatusQuerySupport.queryDeviceStatus("device-1")).willReturn(unknownStatus());

                // When & Then
                assertThatThrownBy(() -> forceStopMachineService.execute(machineId))
                        .isInstanceOf(ExpectedException.class).hasMessage("기기 동작 상태를 확인할 수 없습니다")
                        .hasFieldOrPropertyWithValue("statusCode", HttpStatus.BAD_GATEWAY);

                then(sendDeviceCommandService).shouldHaveNoInteractions();
                then(transactionTemplate).should(never()).execute(any());
                then(reservationRepository).shouldHaveNoInteractions();
                then(machineRepository).should(never()).save(any(Machine.class));
            }
        }

        @Nested
        @DisplayName("SmartThings 명령 전송이 실패하면")
        class Context_when_command_fails {

            @Test
            @DisplayName("DB 예약과 기기 상태를 변경하지 않는다")
            void it_does_not_update_database() {
                // Given
                var machineId = 1L;
                var machine = createMachine(MachineType.WASHER, MachineStatus.NORMAL, MachineAvailability.IN_USE);
                var status = washerStatus("run");
                setId(machine, machineId);

                given(machineRepository.findById(machineId)).willReturn(Optional.of(machine));
                given(deviceStatusQuerySupport.queryDeviceStatus("device-1")).willReturn(status);
                willThrow(new ExpectedException("기기 명령 전송에 실패했습니다", HttpStatus.BAD_GATEWAY))
                        .given(sendDeviceCommandService).execute(eq("device-1"), any(SmartThingsCommandReqDto.class));

                // When & Then
                assertThatThrownBy(() -> forceStopMachineService.execute(machineId))
                        .isInstanceOf(ExpectedException.class).hasMessage("기기 명령 전송에 실패했습니다")
                        .hasFieldOrPropertyWithValue("statusCode", HttpStatus.BAD_GATEWAY);

                then(transactionTemplate).should(never()).execute(any());
                then(reservationRepository).shouldHaveNoInteractions();
                then(machineRepository).should(never()).save(any(Machine.class));
            }
        }

        @Nested
        @DisplayName("고장 상태 기기를 정지하면")
        class Context_with_malfunction_machine {

            @Test
            @DisplayName("기기 상태를 정상으로 바꾸지 않고 사용 불가 상태를 유지한다")
            void it_keeps_machine_unavailable() {
                // Given
                var machineId = 1L;
                var machine = createMachine(MachineType.WASHER,
                        MachineStatus.MALFUNCTION,
                        MachineAvailability.UNAVAILABLE);
                var status = washerStatus("run");
                setId(machine, machineId);

                given(machineRepository.findById(machineId)).willReturn(Optional.of(machine));
                given(machineRepository.findByIdForUpdate(machineId)).willReturn(Optional.of(machine));
                given(deviceStatusQuerySupport.queryDeviceStatus("device-1")).willReturn(status);
                given(reservationRepository.findFirstActiveReservationByMachineId(machineId,
                        List.of(ReservationStatus.RESERVED, ReservationStatus.RUNNING))).willReturn(List.of());
                given(machineRepository.save(any(Machine.class))).willAnswer(invocation -> invocation.getArgument(0));

                // When
                var result = forceStopMachineService.execute(machineId);

                // Then
                assertThat(result.availability()).isEqualTo(MachineAvailability.UNAVAILABLE);
                assertThat(machine.getStatus()).isEqualTo(MachineStatus.MALFUNCTION);
                assertThat(machine.getAvailability()).isEqualTo(MachineAvailability.UNAVAILABLE);
            }
        }

        @Nested
        @DisplayName("존재하지 않는 기기 ID로 요청하면")
        class Context_with_nonexistent_machine_id {

            @Test
            @DisplayName("ExpectedException을 던진다")
            void it_throws_expected_exception() {
                // Given
                var machineId = 999L;

                given(machineRepository.findById(machineId)).willReturn(Optional.empty());

                // When & Then
                assertThatThrownBy(() -> forceStopMachineService.execute(machineId))
                        .isInstanceOf(ExpectedException.class).hasMessage("기기를 찾을 수 없습니다")
                        .hasFieldOrPropertyWithValue("statusCode", HttpStatus.NOT_FOUND);

                then(deviceStatusQuerySupport).shouldHaveNoInteractions();
                then(sendDeviceCommandService).shouldHaveNoInteractions();
                then(transactionTemplate).should(never()).execute(any());
                then(reservationRepository).shouldHaveNoInteractions();
                then(machineRepository).should(never()).save(any(Machine.class));
            }
        }
    }
}
