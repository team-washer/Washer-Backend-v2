package team.washer.server.v2.domain.reservation.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

import java.time.LocalDateTime;
import java.util.Arrays;
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
import team.washer.server.v2.domain.reservation.dto.response.AdminReservationListResDto;
import team.washer.server.v2.domain.reservation.entity.Reservation;
import team.washer.server.v2.domain.reservation.enums.ReservationStatus;
import team.washer.server.v2.domain.reservation.repository.ReservationRepository;
import team.washer.server.v2.domain.reservation.service.impl.QueryAllReservationsServiceImpl;
import team.washer.server.v2.domain.user.entity.User;

@ExtendWith(MockitoExtension.class)
@DisplayName("QueryAllReservationsServiceImpl 클래스의")
class QueryAllReservationsServiceTest {

    @InjectMocks
    private QueryAllReservationsServiceImpl queryAllReservationsService;

    @Mock
    private ReservationRepository reservationRepository;

    private User createUser(String name, String roomNumber) {
        return User.builder().name(name).studentId("20210001").roomNumber(roomNumber).grade(3).floor(3).penaltyCount(0)
                .build();
    }

    private Machine createMachine(String name) {
        return Machine.builder().name(name).type(MachineType.WASHER).deviceId("device-1").floor(2)
                .position(Position.LEFT).number(1).status(MachineStatus.NORMAL)
                .availability(MachineAvailability.AVAILABLE).build();
    }

    private Reservation createReservation(User user, Machine machine, ReservationStatus status) {
        return Reservation.builder().user(user).machine(machine).reservedAt(LocalDateTime.now())
                .startTime(LocalDateTime.now().plusMinutes(10)).status(status).build();
    }

    @Nested
    @DisplayName("execute 메서드는")
    class Describe_execute {

        @Nested
        @DisplayName("사용자 이름으로 필터링할 때")
        class Context_with_user_name_filter {

            @Test
            @DisplayName("사용자 이름을 포함하는 예약 목록을 반환해야 한다")
            void it_returns_reservations_by_user_name() {
                // Given
                String searchName = "김";
                User user1 = createUser("김철수", "301");
                User user2 = createUser("김영희", "302");
                Machine machine = createMachine("W-2F-L1");
                Reservation reservation1 = createReservation(user1, machine, ReservationStatus.RESERVED);
                Reservation reservation2 = createReservation(user2, machine, ReservationStatus.CONFIRMED);
                List<Reservation> reservations = Arrays.asList(reservation1, reservation2);

                given(reservationRepository.findAllWithFilters(searchName, null, null, null, null))
                        .willReturn(reservations);

                // When
                AdminReservationListResDto result = queryAllReservationsService
                        .execute(searchName, null, null, null, null);

                // Then
                assertThat(result.totalCount()).isEqualTo(2);
                assertThat(result.reservations()).allMatch(r -> r.userName().contains("김"));
                then(reservationRepository).should(times(1)).findAllWithFilters(searchName, null, null, null, null);
            }
        }

        @Nested
        @DisplayName("기기명으로 필터링할 때")
        class Context_with_machine_name_filter {

            @Test
            @DisplayName("기기명을 포함하는 예약 목록을 반환해야 한다")
            void it_returns_reservations_by_machine_name() {
                // Given
                String searchMachineName = "W-2F";
                User user = createUser("김철수", "301");
                Machine machine = createMachine("W-2F-L1");
                Reservation reservation = createReservation(user, machine, ReservationStatus.RESERVED);
                List<Reservation> reservations = List.of(reservation);

                given(reservationRepository.findAllWithFilters(null, searchMachineName, null, null, null))
                        .willReturn(reservations);

                // When
                AdminReservationListResDto result = queryAllReservationsService
                        .execute(null, searchMachineName, null, null, null);

                // Then
                assertThat(result.totalCount()).isEqualTo(1);
                assertThat(result.reservations().get(0).machineName()).contains("W-2F");
                then(reservationRepository).should(times(1))
                        .findAllWithFilters(null, searchMachineName, null, null, null);
            }
        }

        @Nested
        @DisplayName("예약 상태로 필터링할 때")
        class Context_with_status_filter {

            @Test
            @DisplayName("해당 상태의 예약 목록을 반환해야 한다")
            void it_returns_reservations_by_status() {
                // Given
                ReservationStatus status = ReservationStatus.RESERVED;
                User user = createUser("김철수", "301");
                Machine machine = createMachine("W-2F-L1");
                Reservation reservation = createReservation(user, machine, ReservationStatus.RESERVED);
                List<Reservation> reservations = List.of(reservation);

                given(reservationRepository.findAllWithFilters(null, null, status, null, null))
                        .willReturn(reservations);

                // When
                AdminReservationListResDto result = queryAllReservationsService.execute(null, null, status, null, null);

                // Then
                assertThat(result.totalCount()).isEqualTo(1);
                assertThat(result.reservations().get(0).status()).isEqualTo(ReservationStatus.RESERVED);
                then(reservationRepository).should(times(1)).findAllWithFilters(null, null, status, null, null);
            }
        }

        @Nested
        @DisplayName("날짜 범위로 필터링할 때")
        class Context_with_date_range_filter {

            @Test
            @DisplayName("해당 날짜 범위의 예약 목록을 반환해야 한다")
            void it_returns_reservations_by_date_range() {
                // Given
                LocalDateTime startDate = LocalDateTime.now();
                LocalDateTime endDate = LocalDateTime.now().plusDays(7);
                User user = createUser("김철수", "301");
                Machine machine = createMachine("W-2F-L1");
                Reservation reservation = createReservation(user, machine, ReservationStatus.RESERVED);
                List<Reservation> reservations = List.of(reservation);

                given(reservationRepository.findAllWithFilters(null, null, null, startDate, endDate))
                        .willReturn(reservations);

                // When
                AdminReservationListResDto result = queryAllReservationsService
                        .execute(null, null, null, startDate, endDate);

                // Then
                assertThat(result.totalCount()).isEqualTo(1);
                then(reservationRepository).should(times(1)).findAllWithFilters(null, null, null, startDate, endDate);
            }
        }

        @Nested
        @DisplayName("필터가 없을 때")
        class Context_without_filter {

            @Test
            @DisplayName("모든 예약 목록을 반환해야 한다")
            void it_returns_all_reservations() {
                // Given
                User user1 = createUser("김철수", "301");
                User user2 = createUser("이영희", "302");
                Machine machine = createMachine("W-2F-L1");
                Reservation reservation1 = createReservation(user1, machine, ReservationStatus.RESERVED);
                Reservation reservation2 = createReservation(user2, machine, ReservationStatus.CONFIRMED);
                List<Reservation> reservations = Arrays.asList(reservation1, reservation2);

                given(reservationRepository.findAllWithFilters(null, null, null, null, null)).willReturn(reservations);

                // When
                AdminReservationListResDto result = queryAllReservationsService.execute(null, null, null, null, null);

                // Then
                assertThat(result.totalCount()).isEqualTo(2);
                then(reservationRepository).should(times(1)).findAllWithFilters(null, null, null, null, null);
            }
        }

        @Nested
        @DisplayName("여러 조건으로 동시에 필터링할 때")
        class Context_with_multiple_filters {

            @Test
            @DisplayName("모든 조건을 AND로 조합하여 예약을 반환해야 한다")
            void it_returns_reservations_matching_all_conditions() {
                // Given
                String userName = "김";
                String machineName = "W-2F";
                ReservationStatus status = ReservationStatus.RESERVED;
                LocalDateTime startDate = LocalDateTime.now();
                LocalDateTime endDate = LocalDateTime.now().plusDays(7);
                User user = createUser("김철수", "301");
                Machine machine = createMachine("W-2F-L1");
                Reservation reservation = createReservation(user, machine, ReservationStatus.RESERVED);
                List<Reservation> reservations = List.of(reservation);

                given(reservationRepository.findAllWithFilters(userName, machineName, status, startDate, endDate))
                        .willReturn(reservations);

                // When
                AdminReservationListResDto result = queryAllReservationsService
                        .execute(userName, machineName, status, startDate, endDate);

                // Then
                assertThat(result.totalCount()).isEqualTo(1);
                assertThat(result.reservations().get(0).userName()).contains("김");
                assertThat(result.reservations().get(0).machineName()).contains("W-2F");
                assertThat(result.reservations().get(0).status()).isEqualTo(ReservationStatus.RESERVED);
                then(reservationRepository).should(times(1))
                        .findAllWithFilters(userName, machineName, status, startDate, endDate);
            }
        }
    }
}
