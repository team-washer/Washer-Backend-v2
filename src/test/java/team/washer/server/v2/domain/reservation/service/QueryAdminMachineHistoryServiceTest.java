package team.washer.server.v2.domain.reservation.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

import java.time.LocalDateTime;
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
import team.washer.server.v2.domain.reservation.dto.response.AdminMachineHistoryResDto;
import team.washer.server.v2.domain.reservation.entity.Reservation;
import team.washer.server.v2.domain.reservation.enums.ReservationStatus;
import team.washer.server.v2.domain.reservation.repository.ReservationRepository;
import team.washer.server.v2.domain.reservation.service.impl.QueryAdminMachineHistoryServiceImpl;
import team.washer.server.v2.domain.user.entity.User;

@ExtendWith(MockitoExtension.class)
@DisplayName("QueryAdminMachineHistoryServiceImpl 클래스의")
class QueryAdminMachineHistoryServiceTest {

    @InjectMocks
    private QueryAdminMachineHistoryServiceImpl queryAdminMachineHistoryService;

    @Mock
    private ReservationRepository reservationRepository;

    private User createUser(String roomNumber) {
        return User.builder().name("테스트유저").studentId("20210001").roomNumber(roomNumber).grade(3).floor(3)
                .penaltyCount(0).build();
    }

    private Machine createMachine(String name) {
        return Machine.builder().name(name).type(MachineType.WASHER).deviceId("device-1").floor(2)
                .position(Position.LEFT).number(1).status(MachineStatus.NORMAL)
                .availability(MachineAvailability.AVAILABLE).build();
    }

    private Reservation createReservation(User user, Machine machine, ReservationStatus status) {
        return Reservation.builder().user(user).machine(machine).reservedAt(LocalDateTime.now()).status(status).build();
    }

    private Reservation createCompletedReservation(User user, Machine machine) {
        return Reservation.builder().user(user).machine(machine).reservedAt(LocalDateTime.now().minusHours(2))
                .startTime(LocalDateTime.now().minusHours(1))
                .actualCompletionTime(LocalDateTime.now())
                .status(ReservationStatus.COMPLETED).build();
    }

    private Reservation createCancelledReservation(User user, Machine machine) {
        return Reservation.builder().user(user).machine(machine).reservedAt(LocalDateTime.now().minusMinutes(10))
                .cancelledAt(LocalDateTime.now().minusMinutes(7))
                .status(ReservationStatus.CANCELLED).build();
    }

    @Nested
    @DisplayName("execute 메서드는")
    class Describe_execute {

        @Nested
        @DisplayName("기기명 필터 없이 호출할 때")
        class Context_without_machine_name_filter {

            @Test
            @DisplayName("전체 기기의 예약 히스토리를 기기명 오름차순으로 그룹핑하여 반환해야 한다")
            void it_returns_all_machines_history_grouped_by_name() {
                // Given
                Machine machineA = createMachine("W-2F-L1");
                Machine machineB = createMachine("W-3F-L1");
                User user1 = createUser("201");
                User user2 = createUser("301");
                Reservation r1 = createReservation(user1, machineA, ReservationStatus.RESERVED);
                Reservation r2 = createCompletedReservation(user2, machineB);

                given(reservationRepository.findAllByMachineNameFilter(null)).willReturn(List.of(r1, r2));

                // When
                AdminMachineHistoryResDto result = queryAdminMachineHistoryService.execute(null);

                // Then
                assertThat(result.machines()).hasSize(2);
                assertThat(result.machines().get(0).machineName()).isEqualTo("W-2F-L1");
                assertThat(result.machines().get(1).machineName()).isEqualTo("W-3F-L1");
                then(reservationRepository).should(times(1)).findAllByMachineNameFilter(null);
            }
        }

        @Nested
        @DisplayName("기기명 필터로 호출할 때")
        class Context_with_machine_name_filter {

            @Test
            @DisplayName("해당 기기명을 포함하는 기기의 예약 히스토리만 반환해야 한다")
            void it_returns_filtered_machine_history() {
                // Given
                String filter = "W-2F";
                Machine machine = createMachine("W-2F-L1");
                User user = createUser("201");
                Reservation reservation = createReservation(user, machine, ReservationStatus.RUNNING);

                given(reservationRepository.findAllByMachineNameFilter(filter)).willReturn(List.of(reservation));

                // When
                AdminMachineHistoryResDto result = queryAdminMachineHistoryService.execute(filter);

                // Then
                assertThat(result.machines()).hasSize(1);
                assertThat(result.machines().get(0).machineName()).isEqualTo("W-2F-L1");
                assertThat(result.machines().get(0).reservations()).hasSize(1);
                assertThat(result.machines().get(0).reservations().get(0).roomNumber()).isEqualTo("201");
                then(reservationRepository).should(times(1)).findAllByMachineNameFilter(filter);
            }
        }

        @Nested
        @DisplayName("한 기기에 여러 상태의 예약이 있을 때")
        class Context_with_multiple_status_reservations {

            @Test
            @DisplayName("완료시간과 취소시간이 각각 올바르게 매핑되어야 한다")
            void it_maps_completion_and_cancellation_times_correctly() {
                // Given
                Machine machine = createMachine("W-2F-L1");
                User user = createUser("201");
                Reservation completed = createCompletedReservation(user, machine);
                Reservation cancelled = createCancelledReservation(user, machine);

                given(reservationRepository.findAllByMachineNameFilter(null))
                        .willReturn(List.of(completed, cancelled));

                // When
                AdminMachineHistoryResDto result = queryAdminMachineHistoryService.execute(null);

                // Then
                assertThat(result.machines()).hasSize(1);
                var reservations = result.machines().get(0).reservations();
                assertThat(reservations).hasSize(2);

                var completedDto = reservations.stream()
                        .filter(r -> r.status() == ReservationStatus.COMPLETED).findFirst().orElseThrow();
                assertThat(completedDto.actualCompletionTime()).isNotNull();
                assertThat(completedDto.cancelledAt()).isNull();

                var cancelledDto = reservations.stream()
                        .filter(r -> r.status() == ReservationStatus.CANCELLED).findFirst().orElseThrow();
                assertThat(cancelledDto.cancelledAt()).isNotNull();
                assertThat(cancelledDto.actualCompletionTime()).isNull();
            }
        }

        @Nested
        @DisplayName("예약이 없을 때")
        class Context_with_no_reservations {

            @Test
            @DisplayName("빈 기기 목록을 반환해야 한다")
            void it_returns_empty_list() {
                // Given
                given(reservationRepository.findAllByMachineNameFilter(null)).willReturn(List.of());

                // When
                AdminMachineHistoryResDto result = queryAdminMachineHistoryService.execute(null);

                // Then
                assertThat(result.machines()).isEmpty();
                then(reservationRepository).should(times(1)).findAllByMachineNameFilter(null);
            }
        }
    }
}