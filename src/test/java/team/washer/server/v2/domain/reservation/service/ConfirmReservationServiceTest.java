package team.washer.server.v2.domain.reservation.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

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
import team.washer.server.v2.domain.machine.repository.MachineRepository;
import team.washer.server.v2.domain.reservation.entity.Reservation;
import team.washer.server.v2.domain.reservation.repository.ReservationRepository;
import team.washer.server.v2.domain.reservation.service.impl.ConfirmReservationServiceImpl;
import team.washer.server.v2.domain.user.entity.User;
import team.washer.server.v2.global.security.provider.CurrentUserProvider;

@ExtendWith(MockitoExtension.class)
class ConfirmReservationServiceTest {

    @InjectMocks
    private ConfirmReservationServiceImpl confirmReservationService;

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private MachineRepository machineRepository;

    @Mock
    private CurrentUserProvider currentUserProvider;

    @Mock
    private Reservation reservation;

    @Mock
    private Machine machine;

    @Mock
    private User user;

    private static final Long USER_ID = 100L;

    @Nested
    @DisplayName("예약 확인")
    class ExecuteTest {

        @Test
        @DisplayName("본인의 예약을 성공적으로 확인한다")
        void execute_ShouldConfirmReservation_WhenValidUserAndReservation() {
            // Given
            when(currentUserProvider.getCurrentUserId()).thenReturn(USER_ID);
            Long reservationId = 1L;

            when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(reservation));
            when(reservation.getUser()).thenReturn(user);
            when(user.getId()).thenReturn(USER_ID);
            when(reservation.getMachine()).thenReturn(machine);

            // When
            confirmReservationService.execute(reservationId);

            // Then
            verify(reservation, times(1)).confirm();
            verify(reservationRepository, times(1)).save(reservation);
        }

        @Test
        @DisplayName("예약이 존재하지 않으면 예외를 발생시킨다")
        void execute_ShouldThrowException_WhenReservationNotFound() {
            // Given
            when(currentUserProvider.getCurrentUserId()).thenReturn(USER_ID);
            Long reservationId = 999L;

            when(reservationRepository.findById(reservationId)).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> confirmReservationService.execute(reservationId))
                    .isInstanceOf(ExpectedException.class).hasMessage("예약을 찾을 수 없습니다");
        }

        @Test
        @DisplayName("다른 사용자의 예약을 확인하려고 하면 예외를 발생시킨다")
        void execute_ShouldThrowException_WhenUnauthorizedUser() {
            // Given
            when(currentUserProvider.getCurrentUserId()).thenReturn(USER_ID);
            Long reservationId = 1L;
            Long differentUserId = 200L;

            when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(reservation));
            when(reservation.getUser()).thenReturn(user);
            when(user.getId()).thenReturn(differentUserId);

            // When & Then
            assertThatThrownBy(() -> confirmReservationService.execute(reservationId))
                    .isInstanceOf(ExpectedException.class).hasMessage("본인의 예약만 확인할 수 있습니다");
        }
    }
}
