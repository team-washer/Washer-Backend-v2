package team.washer.server.v2.domain.reservation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
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
import team.washer.server.v2.domain.machine.enums.MachineType;
import team.washer.server.v2.domain.machine.repository.MachineRepository;
import team.washer.server.v2.domain.reservation.config.ReservationEnvironment;
import team.washer.server.v2.domain.reservation.dto.request.CreateReservationReqDto;
import team.washer.server.v2.domain.reservation.dto.response.ReservationResDto;
import team.washer.server.v2.domain.reservation.entity.Reservation;
import team.washer.server.v2.domain.reservation.repository.ReservationRepository;
import team.washer.server.v2.domain.reservation.service.impl.CreateReservationServiceImpl;
import team.washer.server.v2.domain.reservation.util.PenaltyRedisUtil;
import team.washer.server.v2.domain.reservation.util.SundayReservationRedisUtil;
import team.washer.server.v2.domain.user.entity.User;
import team.washer.server.v2.domain.user.repository.UserRepository;
import team.washer.server.v2.global.security.provider.CurrentUserProvider;

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
    private ReservationEnvironment reservationEnvironment;
    @Mock
    private CurrentUserProvider currentUserProvider;

    @Mock
    private User user;
    @Mock
    private Machine machine;
    @Mock
    private Reservation reservation;

    private static final Long USER_ID = 1L;
    private static final String ROOM_NUMBER = "101";

    @Nested
    @DisplayName("예약 생성")
    class ExecuteTest {

        @Test
        @DisplayName("유효한 요청이면 예약이 정상적으로 생성된다")
        void execute_ShouldCreateReservation_WhenValidRequest() {
            // Given
            when(currentUserProvider.getCurrentUserId()).thenReturn(USER_ID);
            final var reqDto = new CreateReservationReqDto(1L, LocalDateTime.now().plusHours(1));

            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
            when(machineRepository.findById(reqDto.machineId())).thenReturn(Optional.of(machine));
            when(penaltyRedisUtil.getPenaltyExpiryTime(USER_ID)).thenReturn(null);
            when(reservationEnvironment.disableTimeRestriction()).thenReturn(true);
            when(user.getRoomNumber()).thenReturn(ROOM_NUMBER);
            when(machine.getType()).thenReturn(MachineType.WASHER);
            when(reservationRepository.existsByUserAndStatusIn(eq(user), any())).thenReturn(false);
            when(reservationRepository.existsActiveReservationByRoomAndMachineType(ROOM_NUMBER, MachineType.WASHER))
                    .thenReturn(false);
            when(reservationRepository.existsConflictingReservation(any(), any(), any(), any())).thenReturn(false);
            when(reservationRepository.save(any(Reservation.class))).thenReturn(reservation);

            when(reservation.getId()).thenReturn(1L);
            when(reservation.getUser()).thenReturn(user);
            when(reservation.getMachine()).thenReturn(machine);
            when(reservation.getStartTime()).thenReturn(reqDto.startTime());
            when(user.getId()).thenReturn(USER_ID);
            when(machine.getId()).thenReturn(1L);

            // When
            final ReservationResDto result = createReservationService.execute(reqDto);

            // Then
            assertThat(result).isNotNull();
            verify(reservationRepository).save(any(Reservation.class));
        }

        @Test
        @DisplayName("세탁기와 건조기는 동시에 각 1개씩 예약할 수 있다")
        void execute_ShouldCreateReservation_WhenRoomHasDifferentTypeActiveReservation() {
            // Given
            when(currentUserProvider.getCurrentUserId()).thenReturn(USER_ID);
            final var reqDto = new CreateReservationReqDto(2L, LocalDateTime.now().plusHours(1));

            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
            when(machineRepository.findById(reqDto.machineId())).thenReturn(Optional.of(machine));
            when(penaltyRedisUtil.getPenaltyExpiryTime(USER_ID)).thenReturn(null);
            when(reservationEnvironment.disableTimeRestriction()).thenReturn(true);
            when(user.getRoomNumber()).thenReturn(ROOM_NUMBER);
            when(machine.getType()).thenReturn(MachineType.DRYER);
            // 룸메이트가 세탁기 예약 중 → 본인은 활성 예약 없음, 건조기 유형도 호실에 없음
            when(reservationRepository.existsByUserAndStatusIn(eq(user), any())).thenReturn(false);
            when(reservationRepository.existsActiveReservationByRoomAndMachineType(ROOM_NUMBER, MachineType.DRYER))
                    .thenReturn(false);
            when(reservationRepository.existsConflictingReservation(any(), any(), any(), any())).thenReturn(false);
            when(reservationRepository.save(any(Reservation.class))).thenReturn(reservation);

            when(reservation.getId()).thenReturn(2L);
            when(reservation.getUser()).thenReturn(user);
            when(reservation.getMachine()).thenReturn(machine);
            when(reservation.getStartTime()).thenReturn(reqDto.startTime());
            when(user.getId()).thenReturn(USER_ID);
            when(machine.getId()).thenReturn(2L);

            // When
            final ReservationResDto result = createReservationService.execute(reqDto);

            // Then
            assertThat(result).isNotNull();
            verify(reservationRepository).save(any(Reservation.class));
        }

        @Test
        @DisplayName("이미 활성 예약이 있는 개인이 추가 예약을 시도하면 예외를 발생시킨다")
        void execute_ShouldThrowException_WhenUserAlreadyHasActiveReservation() {
            // Given
            when(currentUserProvider.getCurrentUserId()).thenReturn(USER_ID);
            final var reqDto = new CreateReservationReqDto(1L, LocalDateTime.now().plusHours(1));

            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
            when(machineRepository.findById(reqDto.machineId())).thenReturn(Optional.of(machine));
            when(penaltyRedisUtil.getPenaltyExpiryTime(USER_ID)).thenReturn(null);
            when(reservationEnvironment.disableTimeRestriction()).thenReturn(true);
            when(reservationRepository.existsByUserAndStatusIn(eq(user), any())).thenReturn(true);

            // When & Then
            assertThatThrownBy(() -> createReservationService.execute(reqDto)).isInstanceOf(ExpectedException.class)
                    .hasMessageContaining("1인 1예약");
        }

        @Test
        @DisplayName("동일 호실에 같은 유형의 활성 예약이 있으면 예외를 발생시킨다")
        void execute_ShouldThrowException_WhenRoomAlreadyHasSameTypeActiveReservation() {
            // Given
            when(currentUserProvider.getCurrentUserId()).thenReturn(USER_ID);
            final var reqDto = new CreateReservationReqDto(1L, LocalDateTime.now().plusHours(1));

            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
            when(machineRepository.findById(reqDto.machineId())).thenReturn(Optional.of(machine));
            when(penaltyRedisUtil.getPenaltyExpiryTime(USER_ID)).thenReturn(null);
            when(reservationEnvironment.disableTimeRestriction()).thenReturn(true);
            when(user.getRoomNumber()).thenReturn(ROOM_NUMBER);
            when(machine.getType()).thenReturn(MachineType.WASHER);
            when(reservationRepository.existsByUserAndStatusIn(eq(user), any())).thenReturn(false);
            when(reservationRepository.existsActiveReservationByRoomAndMachineType(ROOM_NUMBER, MachineType.WASHER))
                    .thenReturn(true);

            // When & Then
            assertThatThrownBy(() -> createReservationService.execute(reqDto)).isInstanceOf(ExpectedException.class)
                    .hasMessageContaining("세탁기");
        }

        @Test
        @DisplayName("해당 시간에 기기가 이미 예약되어 있으면 예외를 발생시킨다")
        void execute_ShouldThrowException_WhenMachineHasConflictingReservation() {
            // Given
            when(currentUserProvider.getCurrentUserId()).thenReturn(USER_ID);
            final var reqDto = new CreateReservationReqDto(1L, LocalDateTime.now().plusHours(1));

            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
            when(machineRepository.findById(reqDto.machineId())).thenReturn(Optional.of(machine));
            when(penaltyRedisUtil.getPenaltyExpiryTime(USER_ID)).thenReturn(null);
            when(reservationEnvironment.disableTimeRestriction()).thenReturn(true);
            when(user.getRoomNumber()).thenReturn(ROOM_NUMBER);
            when(machine.getType()).thenReturn(MachineType.WASHER);
            when(reservationRepository.existsByUserAndStatusIn(eq(user), any())).thenReturn(false);
            when(reservationRepository.existsActiveReservationByRoomAndMachineType(ROOM_NUMBER, MachineType.WASHER))
                    .thenReturn(false);
            when(reservationRepository.existsConflictingReservation(any(), any(), any(), any())).thenReturn(true);
            when(machine.getName()).thenReturn("세탁기-1");

            // When & Then
            assertThatThrownBy(() -> createReservationService.execute(reqDto)).isInstanceOf(ExpectedException.class)
                    .hasMessageContaining("해당 시간에 기기를 사용할 수 없습니다");
        }
    }
}
