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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;

import team.themoment.sdk.exception.ExpectedException;
import team.washer.server.v2.domain.machine.entity.Machine;
import team.washer.server.v2.domain.machine.enums.MachineAvailability;
import team.washer.server.v2.domain.machine.enums.MachineStatus;
import team.washer.server.v2.domain.machine.enums.MachineType;
import team.washer.server.v2.domain.machine.enums.Position;
import team.washer.server.v2.domain.machine.repository.MachineRepository;
import team.washer.server.v2.domain.reservation.entity.Reservation;
import team.washer.server.v2.domain.reservation.enums.ReservationStatus;
import team.washer.server.v2.domain.reservation.repository.ReservationRepository;
import team.washer.server.v2.domain.reservation.service.impl.QueryMachineReservationHistoryServiceImpl;
import team.washer.server.v2.domain.user.entity.User;

@ExtendWith(MockitoExtension.class)
@DisplayName("QueryMachineReservationHistoryServiceImpl 클래스의")
class QueryMachineReservationHistoryServiceTest {

    @InjectMocks
    private QueryMachineReservationHistoryServiceImpl queryMachineReservationHistoryService;

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private MachineRepository machineRepository;

    private User createUser() {
        return User.builder().name("김철수").studentId("20210001").roomNumber("301").grade(3).floor(3).build();
    }

    private Machine createMachine() {
        return Machine.builder().name("W-2F-L1").type(MachineType.WASHER).deviceId("device-1").floor(2)
                .position(Position.LEFT).number(1).status(MachineStatus.NORMAL)
                .availability(MachineAvailability.AVAILABLE).build();
    }

    @Nested
    @DisplayName("execute 메서드는")
    class Describe_execute {

        @Nested
        @DisplayName("존재하는 기기의 예약 히스토리를 조회할 때")
        class Context_with_existing_machine {

            @Test
            @DisplayName("페이징된 예약 히스토리를 반환해야 한다")
            void it_returns_paged_history() {
                // Given
                var machineId = 1L;
                var pageable = PageRequest.of(0, 10);
                var user = createUser();
                var machine = createMachine();
                var reservation = Reservation.builder().user(user).machine(machine).reservedAt(LocalDateTime.now())
                        .status(ReservationStatus.COMPLETED).build();
                var page = new PageImpl<>(List.of(reservation), pageable, 1);

                given(machineRepository.existsById(machineId)).willReturn(true);
                given(reservationRepository.findMachineReservationHistory(machineId, null, null, null, pageable))
                        .willReturn(page);

                // When
                var result = queryMachineReservationHistoryService.execute(machineId, null, null, null, pageable);

                // Then
                assertThat(result).isNotNull();
                assertThat(result.content()).hasSize(1);
                assertThat(result.totalElements()).isEqualTo(1L);
            }
        }

        @Nested
        @DisplayName("존재하지 않는 기기의 예약 히스토리를 조회할 때")
        class Context_with_nonexistent_machine {

            @Test
            @DisplayName("ExpectedException이 발생하고 NOT_FOUND 상태를 반환해야 한다")
            void it_throws_not_found_exception() {
                // Given
                var machineId = 999L;
                var pageable = PageRequest.of(0, 10);
                given(machineRepository.existsById(machineId)).willReturn(false);

                // When & Then
                assertThatThrownBy(() -> queryMachineReservationHistoryService.execute(
                        machineId, null, null, null, pageable))
                        .isInstanceOf(ExpectedException.class)
                        .hasMessage("존재하지 않는 기기입니다")
                        .satisfies(e -> assertThat(((ExpectedException) e).getStatusCode())
                                .isEqualTo(HttpStatus.NOT_FOUND));

                then(reservationRepository).shouldHaveNoInteractions();
            }
        }
    }
}
