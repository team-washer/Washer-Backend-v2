package team.washer.server.v2.domain.reservation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import team.washer.server.v2.domain.machine.entity.Machine;
import team.washer.server.v2.domain.machine.repository.MachineRepository;
import team.washer.server.v2.domain.reservation.dto.request.CreateReservationReqDto;
import team.washer.server.v2.domain.reservation.dto.response.ReservationResDto;
import team.washer.server.v2.domain.reservation.entity.Reservation;
import team.washer.server.v2.domain.reservation.repository.ReservationRepository;
import team.washer.server.v2.domain.reservation.service.impl.CreateReservationServiceImpl;
import team.washer.server.v2.domain.reservation.util.PenaltyRedisUtil;
import team.washer.server.v2.domain.reservation.util.SundayReservationRedisUtil;
import team.washer.server.v2.domain.user.entity.User;
import team.washer.server.v2.domain.user.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class CreateReservationServiceTest {

    @InjectMocks
    private CreateReservationServiceImpl createReservationService;

    @Mock
    private ReservationRepository reservationRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private MachineRepository machineRepository;
    @Mock
    private PenaltyRedisUtil penaltyRedisUtil;
    @Mock
    private SundayReservationRedisUtil sundayReservationRedisUtil;

    @Mock
    private User user;
    @Mock
    private Machine machine;
    @Mock
    private Reservation reservation;

    @Test
    @DisplayName("정상적인 예약 생성 요청이 들어오면 예약이 생성된다")
    void execute_ShouldCreateReservation_WhenValidRequest() {
        // Given
        Long userId = 1L;
        CreateReservationReqDto reqDto = new CreateReservationReqDto(1L, LocalDateTime.now().plusHours(1));

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(machineRepository.findById(reqDto.machineId())).thenReturn(Optional.of(machine));
        when(penaltyRedisUtil.getPenaltyExpiryTime(userId)).thenReturn(null);
        when(sundayReservationRedisUtil.isSundayActive()).thenReturn(false);
        when(reservationRepository.existsConflictingReservation(any(), any(), any(), any())).thenReturn(false);
        when(reservationRepository.save(any(Reservation.class))).thenReturn(reservation);

        when(reservation.getId()).thenReturn(1L);
        when(reservation.getUser()).thenReturn(user);
        when(reservation.getMachine()).thenReturn(machine);
        when(reservation.getStartTime()).thenReturn(reqDto.startTime());
        when(user.getId()).thenReturn(userId);
        when(machine.getId()).thenReturn(1L);

        // When
        ReservationResDto result = createReservationService.execute(userId, reqDto);

        // Then
        assertThat(result).isNotNull();
        verify(reservationRepository).save(any(Reservation.class));
    }
}
