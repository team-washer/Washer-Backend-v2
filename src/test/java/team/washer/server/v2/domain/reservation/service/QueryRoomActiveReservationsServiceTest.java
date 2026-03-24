package team.washer.server.v2.domain.reservation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
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
import team.washer.server.v2.domain.reservation.dto.response.RoomActiveReservationsResDto;
import team.washer.server.v2.domain.reservation.entity.Reservation;
import team.washer.server.v2.domain.reservation.enums.ReservationStatus;
import team.washer.server.v2.domain.reservation.repository.ReservationRepository;
import team.washer.server.v2.domain.reservation.service.impl.QueryRoomActiveReservationsServiceImpl;
import team.washer.server.v2.domain.user.entity.User;
import team.washer.server.v2.domain.user.repository.UserRepository;
import team.washer.server.v2.global.security.provider.CurrentUserProvider;

@ExtendWith(MockitoExtension.class)
class QueryRoomActiveReservationsServiceTest {

    @InjectMocks
    private QueryRoomActiveReservationsServiceImpl queryRoomActiveReservationsService;

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
    private static final String ROOM_NUMBER = "301";

    @Nested
    @DisplayName("호실 활성 예약 목록 조회")
    class ExecuteTest {

        @Test
        @DisplayName("만료되지 않은 활성 예약이 있으면 해당 목록을 반환한다")
        void execute_ShouldReturnReservations_WhenValidActiveReservationsExist() {
            // Given
            when(currentUserProvider.getCurrentUserId()).thenReturn(USER_ID);
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
            when(user.getRoomNumber()).thenReturn(ROOM_NUMBER);
            final Reservation reservation = mock(Reservation.class);
            when(reservationRepository.findActiveReservationsByRoomNumber(ROOM_NUMBER))
                    .thenReturn(List.of(reservation));
            when(reservation.isExpired()).thenReturn(false);
            stubReservationDtoFields(reservation, 1L);

            // When
            final RoomActiveReservationsResDto result = queryRoomActiveReservationsService.execute();

            // Then
            assertThat(result.reservations()).hasSize(1);
            assertThat(result.reservations().get(0).id()).isEqualTo(1L);
        }

        @Test
        @DisplayName("호실 내 세탁기·건조기 예약이 모두 있으면 2건을 반환한다")
        void execute_ShouldReturnTwoReservations_WhenBothWasherAndDryerReservationsExist() {
            // Given
            when(currentUserProvider.getCurrentUserId()).thenReturn(USER_ID);
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
            when(user.getRoomNumber()).thenReturn(ROOM_NUMBER);
            final Reservation washerReservation = mock(Reservation.class);
            final Reservation dryerReservation = mock(Reservation.class);
            when(reservationRepository.findActiveReservationsByRoomNumber(ROOM_NUMBER))
                    .thenReturn(List.of(washerReservation, dryerReservation));
            when(washerReservation.isExpired()).thenReturn(false);
            when(dryerReservation.isExpired()).thenReturn(false);
            stubReservationDtoFields(washerReservation, 1L);
            stubReservationDtoFields(dryerReservation, 2L);

            // When
            final RoomActiveReservationsResDto result = queryRoomActiveReservationsService.execute();

            // Then
            assertThat(result.reservations()).hasSize(2);
        }

        @Test
        @DisplayName("만료 예약과 유효 예약이 혼재하면 유효한 예약만 반환한다")
        void execute_ShouldReturnOnlyValidReservations_WhenMixedExpiredAndValidReservationsExist() {
            // Given
            when(currentUserProvider.getCurrentUserId()).thenReturn(USER_ID);
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
            when(user.getRoomNumber()).thenReturn(ROOM_NUMBER);
            final Reservation expiredReservation = mock(Reservation.class);
            final Reservation validReservation = mock(Reservation.class);
            when(reservationRepository.findActiveReservationsByRoomNumber(ROOM_NUMBER))
                    .thenReturn(List.of(expiredReservation, validReservation));
            when(expiredReservation.isExpired()).thenReturn(true);
            when(validReservation.isExpired()).thenReturn(false);
            stubReservationDtoFields(validReservation, 2L);

            // When
            final RoomActiveReservationsResDto result = queryRoomActiveReservationsService.execute();

            // Then
            assertThat(result.reservations()).hasSize(1);
            assertThat(result.reservations().get(0).id()).isEqualTo(2L);
        }

        @Test
        @DisplayName("만료된 예약만 존재하면 빈 목록을 반환한다")
        void execute_ShouldReturnEmptyList_WhenOnlyExpiredReservationsExist() {
            // Given
            when(currentUserProvider.getCurrentUserId()).thenReturn(USER_ID);
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
            when(user.getRoomNumber()).thenReturn(ROOM_NUMBER);
            final Reservation expiredReservation = mock(Reservation.class);
            when(reservationRepository.findActiveReservationsByRoomNumber(ROOM_NUMBER))
                    .thenReturn(List.of(expiredReservation));
            when(expiredReservation.isExpired()).thenReturn(true);

            // When
            final RoomActiveReservationsResDto result = queryRoomActiveReservationsService.execute();

            // Then
            assertThat(result.reservations()).isEmpty();
        }

        @Test
        @DisplayName("호실에 활성 예약이 없으면 빈 목록을 반환한다")
        void execute_ShouldReturnEmptyList_WhenNoActiveReservationsExist() {
            // Given
            when(currentUserProvider.getCurrentUserId()).thenReturn(USER_ID);
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
            when(user.getRoomNumber()).thenReturn(ROOM_NUMBER);
            when(reservationRepository.findActiveReservationsByRoomNumber(anyString()))
                    .thenReturn(Collections.emptyList());

            // When
            final RoomActiveReservationsResDto result = queryRoomActiveReservationsService.execute();

            // Then
            assertThat(result.reservations()).isEmpty();
        }

        @Test
        @DisplayName("사용자를 찾을 수 없으면 예외를 발생시킨다")
        void execute_ShouldThrowException_WhenUserNotFound() {
            // Given
            when(currentUserProvider.getCurrentUserId()).thenReturn(USER_ID);
            when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> queryRoomActiveReservationsService.execute()).isInstanceOf(ExpectedException.class)
                    .hasMessage("사용자를 찾을 수 없습니다");
        }
    }

    private void stubReservationDtoFields(Reservation reservation, Long reservationId) {
        when(reservation.getId()).thenReturn(reservationId);
        when(reservation.getUser()).thenReturn(user);
        when(user.getId()).thenReturn(USER_ID);
        when(user.getName()).thenReturn("김철수");
        when(user.getRoomNumber()).thenReturn(ROOM_NUMBER);
        when(reservation.getMachine()).thenReturn(machine);
        when(machine.getId()).thenReturn(1L);
        when(machine.getName()).thenReturn("세탁기 1");
        when(reservation.getReservedAt()).thenReturn(LocalDateTime.now());
        when(reservation.getStartTime()).thenReturn(LocalDateTime.now());
        when(reservation.getStatus()).thenReturn(ReservationStatus.RESERVED);
    }
}
