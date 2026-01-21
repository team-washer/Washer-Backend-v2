package team.washer.server.v2.domain.reservation.scheduler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import team.washer.server.v2.domain.reservation.entity.Reservation;
import team.washer.server.v2.domain.reservation.enums.ReservationStatus;
import team.washer.server.v2.domain.reservation.repository.ReservationRepository;
import team.washer.server.v2.domain.reservation.util.PenaltyRedisUtil;
import team.washer.server.v2.domain.user.entity.User;

@ExtendWith(MockitoExtension.class)
class ReservationTimeoutSchedulerTest {

    @InjectMocks
    private ReservationTimeoutScheduler reservationTimeoutScheduler;

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private PenaltyRedisUtil penaltyRedisUtil;

    @Mock
    private Reservation reservation;

    @Mock
    private User user;

    @Test
    @DisplayName("만료된 RESERVED 예약이 있으면 취소하고 패널티를 부여한다")
    void checkReservationTimeouts_ShouldCancelAndPenalize_WhenExpiredReservedExists() {
        // Given
        when(reservationRepository.findExpiredReservations(eq(ReservationStatus.RESERVED),
                any(LocalDateTime.class),
                any(LocalDateTime.class))).thenReturn(List.of(reservation));

        when(reservation.getUser()).thenReturn(user);

        // When
        reservationTimeoutScheduler.checkReservationTimeouts();

        // Then
        verify(reservation, times(1)).cancel();
        verify(reservationRepository, times(1)).save(reservation);
        verify(penaltyRedisUtil, times(1)).applyPenalty(user);
    }

    @Test
    @DisplayName("만료된 예약이 없으면 아무 동작도 하지 않는다")
    void checkReservationTimeouts_ShouldDoNothing_WhenNoExpiredReservations() {
        // Given
        when(reservationRepository.findExpiredReservations(eq(ReservationStatus.RESERVED),
                any(LocalDateTime.class),
                any(LocalDateTime.class))).thenReturn(Collections.emptyList());

        // When
        reservationTimeoutScheduler.checkReservationTimeouts();

        // Then
        verify(reservation, never()).cancel();
        verify(reservationRepository, never()).save(any());
        verify(penaltyRedisUtil, never()).applyPenalty(any());
    }
}
