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

import team.washer.server.v2.domain.machine.entity.Machine;
import team.washer.server.v2.domain.machine.enums.MachineAvailability;
import team.washer.server.v2.domain.machine.enums.MachineStatus;
import team.washer.server.v2.domain.machine.enums.MachineType;
import team.washer.server.v2.domain.machine.enums.Position;
import team.washer.server.v2.domain.reservation.entity.Reservation;
import team.washer.server.v2.domain.reservation.enums.ReservationStatus;
import team.washer.server.v2.domain.reservation.repository.ReservationRepository;
import team.washer.server.v2.domain.reservation.service.impl.QueryReservationHistoryServiceImpl;
import team.washer.server.v2.domain.user.entity.User;
import team.washer.server.v2.global.security.provider.CurrentUserProvider;

@ExtendWith(MockitoExtension.class)
@DisplayName("QueryReservationHistoryServiceImpl 클래스의")
class QueryReservationHistoryServiceTest {

    @InjectMocks
    private QueryReservationHistoryServiceImpl queryReservationHistoryService;

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private CurrentUserProvider currentUserProvider;

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
        @DisplayName("예약 히스토리가 있는 사용자가 조회할 때")
        class Context_with_reservation_history {

            @Test
            @DisplayName("페이징된 예약 히스토리를 반환해야 한다")
            void it_returns_paged_reservation_history() {
                // Given
                var userId = 1L;
                var pageable = PageRequest.of(0, 10);
                var user = createUser();
                var machine = createMachine();
                var reservation = Reservation.builder().user(user).machine(machine).reservedAt(LocalDateTime.now())
                        .status(ReservationStatus.COMPLETED).build();
                var page = new PageImpl<>(List.of(reservation), pageable, 1);

                given(currentUserProvider.getCurrentUserId()).willReturn(userId);
                given(reservationRepository.findReservationHistory(userId, null, null, null, null, pageable))
                        .willReturn(page);

                // When
                var result = queryReservationHistoryService.execute(null, null, null, null, pageable);

                // Then
                assertThat(result).isNotNull();
                assertThat(result.content()).hasSize(1);
                assertThat(result.totalElements()).isEqualTo(1L);
                assertThat(result.page()).isEqualTo(0);
                assertThat(result.last()).isTrue();
            }
        }

        @Nested
        @DisplayName("예약 히스토리가 없는 사용자가 조회할 때")
        class Context_with_empty_history {

            @Test
            @DisplayName("빈 페이지를 반환해야 한다")
            void it_returns_empty_page() {
                // Given
                var userId = 1L;
                var pageable = PageRequest.of(0, 10);
                var emptyPage = new PageImpl<Reservation>(List.of(), pageable, 0);

                given(currentUserProvider.getCurrentUserId()).willReturn(userId);
                given(reservationRepository.findReservationHistory(userId, null, null, null, null, pageable))
                        .willReturn(emptyPage);

                // When
                var result = queryReservationHistoryService.execute(null, null, null, null, pageable);

                // Then
                assertThat(result).isNotNull();
                assertThat(result.content()).isEmpty();
                assertThat(result.totalElements()).isEqualTo(0L);
            }
        }
    }
}
