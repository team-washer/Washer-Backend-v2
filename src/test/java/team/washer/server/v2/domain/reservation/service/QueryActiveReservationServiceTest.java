package team.washer.server.v2.domain.reservation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import team.themoment.sdk.exception.ExpectedException;
import team.washer.server.v2.domain.machine.entity.Machine;
import team.washer.server.v2.domain.reservation.dto.response.ReservationResDto;
import team.washer.server.v2.domain.reservation.entity.Reservation;
import team.washer.server.v2.domain.reservation.enums.ReservationStatus;
import team.washer.server.v2.domain.reservation.repository.ReservationRepository;
import team.washer.server.v2.domain.reservation.service.impl.QueryActiveReservationServiceImpl;
import team.washer.server.v2.domain.user.entity.User;
import team.washer.server.v2.domain.user.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class QueryActiveReservationServiceTest {

    @InjectMocks
    private QueryActiveReservationServiceImpl queryActiveReservationService;

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private User user;

    @Mock
    private Machine machine;

    private static final Long USER_ID = 1L;

    @BeforeEach
    void setUpSecurityContext() {
        final SecurityContext securityContext = mock(SecurityContext.class);
        final Authentication authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn(USER_ID);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Nested
    @DisplayName("활성 예약 조회")
    class ExecuteTest {

        @Test
        @DisplayName("만료되지 않은 활성 예약이 있으면 해당 예약을 반환한다")
        void execute_ShouldReturnReservation_WhenValidActiveReservationExists() {
            // Given
            final Reservation reservation = mock(Reservation.class);
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
            when(reservationRepository.findByUserAndStatusIn(eq(user), anyList())).thenReturn(List.of(reservation));
            when(reservation.isExpired()).thenReturn(false);
            when(reservation.getCreatedAt()).thenReturn(LocalDateTime.now());
            stubReservationDtoFields(reservation, 1L);

            // When
            final ReservationResDto result = queryActiveReservationService.execute();

            // Then
            assertThat(result).isNotNull();
            assertThat(result.id()).isEqualTo(1L);
        }

        @Test
        @DisplayName("만료 예약과 유효 예약이 혼재하면 유효한 예약을 반환한다")
        void execute_ShouldReturnValidReservation_WhenMixedExpiredAndValidReservationsExist() {
            // Given
            final Reservation expiredReservation = mock(Reservation.class);
            final Reservation validReservation = mock(Reservation.class);
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
            when(reservationRepository.findByUserAndStatusIn(eq(user), anyList()))
                    .thenReturn(List.of(expiredReservation, validReservation));
            when(expiredReservation.isExpired()).thenReturn(true);
            when(validReservation.isExpired()).thenReturn(false);
            when(validReservation.getCreatedAt()).thenReturn(LocalDateTime.now());
            stubReservationDtoFields(validReservation, 2L);

            // When
            final ReservationResDto result = queryActiveReservationService.execute();

            // Then
            assertThat(result).isNotNull();
            assertThat(result.id()).isEqualTo(2L);
        }

        @Test
        @DisplayName("유효한 예약이 여러 개이면 생성 시각이 가장 최근인 예약을 반환한다")
        void execute_ShouldReturnLatestReservation_WhenMultipleValidReservationsExist() {
            // Given
            final Reservation olderReservation = mock(Reservation.class);
            final Reservation newerReservation = mock(Reservation.class);
            final LocalDateTime older = LocalDateTime.now().minusMinutes(10);
            final LocalDateTime newer = LocalDateTime.now();
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
            when(reservationRepository.findByUserAndStatusIn(eq(user), anyList()))
                    .thenReturn(List.of(olderReservation, newerReservation));
            when(olderReservation.isExpired()).thenReturn(false);
            when(newerReservation.isExpired()).thenReturn(false);
            when(olderReservation.getCreatedAt()).thenReturn(older);
            when(newerReservation.getCreatedAt()).thenReturn(newer);
            stubReservationDtoFields(newerReservation, 2L);

            // When
            final ReservationResDto result = queryActiveReservationService.execute();

            // Then
            assertThat(result).isNotNull();
            assertThat(result.id()).isEqualTo(2L);
        }

        @Test
        @DisplayName("만료된 예약만 존재하면 null을 반환한다")
        void execute_ShouldReturnNull_WhenOnlyExpiredReservationsExist() {
            // Given
            final Reservation expiredReservation = mock(Reservation.class);
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
            when(reservationRepository.findByUserAndStatusIn(eq(user), anyList()))
                    .thenReturn(List.of(expiredReservation));
            when(expiredReservation.isExpired()).thenReturn(true);

            // When
            final ReservationResDto result = queryActiveReservationService.execute();

            // Then
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("활성 예약이 없으면 null을 반환한다")
        void execute_ShouldReturnNull_WhenNoActiveReservationsExist() {
            // Given
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
            when(reservationRepository.findByUserAndStatusIn(eq(user), anyList())).thenReturn(Collections.emptyList());

            // When
            final ReservationResDto result = queryActiveReservationService.execute();

            // Then
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("사용자를 찾을 수 없으면 예외를 발생시킨다")
        void execute_ShouldThrowException_WhenUserNotFound() {
            // Given
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
