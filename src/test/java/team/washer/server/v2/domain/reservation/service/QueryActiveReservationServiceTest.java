package team.washer.server.v2.domain.reservation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import team.themoment.sdk.exception.ExpectedException;
import team.washer.server.v2.domain.machine.entity.Machine;
import team.washer.server.v2.domain.reservation.dto.response.ReservationResDto;
import team.washer.server.v2.domain.reservation.entity.Reservation;
import team.washer.server.v2.domain.reservation.enums.ReservationStatus;
import team.washer.server.v2.domain.reservation.repository.ReservationRepository;
import team.washer.server.v2.domain.reservation.service.impl.QueryActiveReservationServiceImpl;
import team.washer.server.v2.domain.user.entity.User;
import team.washer.server.v2.domain.user.repository.UserRepository;
import team.washer.server.v2.global.security.provider.CurrentUserProvider;

@ExtendWith(MockitoExtension.class)
class QueryActiveReservationServiceTest {

    @InjectMocks
    private QueryActiveReservationServiceImpl queryActiveReservationService;

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private CurrentUserProvider currentUserProvider;

    @Mock
    private User user;

    @Mock
    private Machine machine;

    private static final Long USER_ID = 1L;

    @Nested
    @DisplayName("활성 예약 조회")
    class ExecuteTest {

        @Test
        @DisplayName("만료되지 않은 활성 예약이 있으면 해당 예약을 반환한다")
        void execute_ShouldReturnReservation_WhenValidActiveReservationExists() {
            // Given
            when(currentUserProvider.getCurrentUserId()).thenReturn(USER_ID);
            final Reservation reservation = mock(Reservation.class);
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
            when(reservationRepository.findCurrentlyActiveByUser(user)).thenReturn(List.of(reservation));
            stubReservationDtoFields(reservation, 1L);

            // When
            final ReservationResDto result = queryActiveReservationService.execute();

            // Then
            assertThat(result).isNotNull();
            assertThat(result.id()).isEqualTo(1L);
        }

        @Test
        @DisplayName("활성 예약이 여러 개이면 조회 순서상 가장 최근인 첫 예약을 반환한다")
        void execute_ShouldReturnLatestReservation_WhenMultipleActiveReservationsExist() {
            // Given
            when(currentUserProvider.getCurrentUserId()).thenReturn(USER_ID);
            final Reservation newerReservation = mock(Reservation.class);
            final Reservation olderReservation = mock(Reservation.class);
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
            // 리포지토리가 createdAt 내림차순으로 반환한다
            when(reservationRepository.findCurrentlyActiveByUser(user))
                    .thenReturn(List.of(newerReservation, olderReservation));
            stubReservationDtoFields(newerReservation, 2L);

            // When
            final ReservationResDto result = queryActiveReservationService.execute();

            // Then
            assertThat(result).isNotNull();
            assertThat(result.id()).isEqualTo(2L);
        }

        @Test
        @DisplayName("활성 예약이 없으면 null을 반환한다")
        void execute_ShouldReturnNull_WhenNoActiveReservationsExist() {
            // Given
            when(currentUserProvider.getCurrentUserId()).thenReturn(USER_ID);
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
            when(reservationRepository.findCurrentlyActiveByUser(user)).thenReturn(Collections.emptyList());

            // When
            final ReservationResDto result = queryActiveReservationService.execute();

            // Then
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("사용자를 찾을 수 없으면 예외를 발생시킨다")
        void execute_ShouldThrowException_WhenUserNotFound() {
            // Given
            when(currentUserProvider.getCurrentUserId()).thenReturn(USER_ID);
            when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> queryActiveReservationService.execute()).isInstanceOf(ExpectedException.class)
                    .hasMessage("사용자를 찾을 수 없습니다");
        }
    }

    private void stubReservationDtoFields(Reservation reservation, Long reservationId) {
        when(reservation.getId()).thenReturn(reservationId);
        when(reservation.getUser()).thenReturn(user);
        when(user.getId()).thenReturn(USER_ID);
        when(user.getName()).thenReturn("김철수");
        when(user.getRoomNumber()).thenReturn("301");
        when(reservation.getMachine()).thenReturn(machine);
        when(machine.getId()).thenReturn(1L);
        when(machine.getName()).thenReturn("세탁기 1");
        when(reservation.getReservedAt()).thenReturn(LocalDateTime.now());
        when(reservation.getStartTime()).thenReturn(LocalDateTime.now());
        when(reservation.getStatus()).thenReturn(ReservationStatus.RESERVED);
    }
}
