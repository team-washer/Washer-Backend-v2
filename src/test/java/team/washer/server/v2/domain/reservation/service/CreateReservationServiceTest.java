package team.washer.server.v2.domain.reservation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;

import team.themoment.sdk.exception.ExpectedException;
import team.washer.server.v2.domain.admin.repository.WashingBanRepository;
import team.washer.server.v2.domain.machine.entity.Machine;
import team.washer.server.v2.domain.machine.enums.MachineAvailability;
import team.washer.server.v2.domain.machine.enums.MachineStatus;
import team.washer.server.v2.domain.machine.enums.MachineType;
import team.washer.server.v2.domain.machine.repository.MachineRepository;
import team.washer.server.v2.domain.reservation.config.ReservationEnvironment;
import team.washer.server.v2.domain.reservation.dto.request.CreateReservationReqDto;
import team.washer.server.v2.domain.reservation.dto.response.ReservationResDto;
import team.washer.server.v2.domain.reservation.entity.Reservation;
import team.washer.server.v2.domain.reservation.enums.RestrictionStatus;
import team.washer.server.v2.domain.reservation.repository.ReservationRepository;
import team.washer.server.v2.domain.reservation.service.impl.CreateReservationServiceImpl;
import team.washer.server.v2.domain.reservation.support.ReservationCreationSupport;
import team.washer.server.v2.domain.reservation.support.ReservationDeviceStateVerifier;
import team.washer.server.v2.domain.reservation.util.PenaltyRedisUtil;
import team.washer.server.v2.domain.user.entity.User;
import team.washer.server.v2.domain.user.repository.UserRepository;
import team.washer.server.v2.global.common.error.code.ErrorCode;
import team.washer.server.v2.global.common.error.exception.ErrorCodeException;
import team.washer.server.v2.global.security.provider.CurrentUserProvider;

@ExtendWith(MockitoExtension.class)
class CreateReservationServiceTest {

    private CreateReservationServiceImpl createReservationService;

    @Mock
    private ReservationRepository reservationRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private MachineRepository machineRepository;
    @Mock
    private WashingBanRepository washingBanRepository;
    @Mock
    private PenaltyRedisUtil penaltyRedisUtil;
    @Mock
    private ReservationEnvironment reservationEnvironment;
    @Mock
    private CurrentUserProvider currentUserProvider;
    @Mock
    private ReservationDeviceStateVerifier reservationDeviceStateVerifier;
    @Mock
    private PlatformTransactionManager transactionManager;

    @Mock
    private User user;
    @Mock
    private Machine machine;
    @Mock
    private Reservation reservation;

    private static final Long USER_ID = 1L;
    private static final String ROOM_NUMBER = "101";

    // 검증 순서와 에러 메시지를 그대로 검증하기 위해 Support는 실제 구현체를 사용한다
    @BeforeEach
    void setUp() {
        final var reservationCreationSupport = new ReservationCreationSupport(reservationRepository,
                machineRepository,
                washingBanRepository,
                userRepository);
        createReservationService = new CreateReservationServiceImpl(userRepository,
                penaltyRedisUtil,
                reservationEnvironment,
                currentUserProvider,
                reservationCreationSupport,
                reservationDeviceStateVerifier,
                transactionManager);
    }

    @Nested
    @DisplayName("예약 생성")
    class ExecuteTest {

        @Test
        @DisplayName("유효한 요청이면 예약이 정상적으로 생성된다")
        void execute_ShouldCreateReservation_WhenValidRequest() {
            // Given
            when(currentUserProvider.getCurrentUserId()).thenReturn(USER_ID);
            final var reqDto = new CreateReservationReqDto(1L);

            when(userRepository.findByIdForUpdate(USER_ID)).thenReturn(Optional.of(user));
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
            when(machineRepository.findByIdForUpdate(reqDto.machineId())).thenReturn(Optional.of(machine));
            when(machineRepository.findById(reqDto.machineId())).thenReturn(Optional.of(machine));
            when(penaltyRedisUtil.checkCooldown(eq(USER_ID), any())).thenReturn(RestrictionStatus.NONE);
            when(penaltyRedisUtil.checkBlock(ROOM_NUMBER)).thenReturn(RestrictionStatus.NONE);
            when(reservationEnvironment.disableTimeRestriction()).thenReturn(true);
            when(machine.getAvailability()).thenReturn(MachineAvailability.AVAILABLE);
            when(user.getRoomNumber()).thenReturn(ROOM_NUMBER);
            when(machine.getType()).thenReturn(MachineType.WASHER);
            when(reservationRepository.findCurrentlyActiveByMachine(machine)).thenReturn(List.of());
            when(reservationRepository.findByUserAndStatusIn(eq(user), anyList())).thenReturn(List.of());
            when(reservationRepository.findByRoomNumberAndStatusIn(eq(ROOM_NUMBER), anyList())).thenReturn(List.of());
            when(reservationRepository.save(any(Reservation.class))).thenReturn(reservation);

            when(reservation.getId()).thenReturn(1L);
            when(reservation.getUser()).thenReturn(user);
            when(reservation.getMachine()).thenReturn(machine);
            when(user.getId()).thenReturn(USER_ID);
            when(machine.getId()).thenReturn(1L);

            // When
            final ReservationResDto result = createReservationService.execute(reqDto);

            // Then
            assertThat(result).isNotNull();
            verify(reservationRepository).save(any(Reservation.class));
        }

        @Test
        @DisplayName("기기에 만료된 RESERVED 예약만 남아 있으면 만료 처리가 끝날 때까지 새 예약을 막는다")
        void execute_ShouldRejectReservation_WhenOnlyExpiredMachineReservationExists() {
            // Given
            when(currentUserProvider.getCurrentUserId()).thenReturn(USER_ID);
            final var reqDto = new CreateReservationReqDto(1L);
            var machineWithExpiredReservation = Machine.builder().name("세탁기-1").type(MachineType.WASHER)
                    .status(MachineStatus.NORMAL).availability(MachineAvailability.RESERVED).build();

            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
            when(machineRepository.findById(reqDto.machineId())).thenReturn(Optional.of(machineWithExpiredReservation));
            when(penaltyRedisUtil.checkCooldown(eq(USER_ID), any())).thenReturn(RestrictionStatus.NONE);
            when(penaltyRedisUtil.checkBlock(ROOM_NUMBER)).thenReturn(RestrictionStatus.NONE);
            when(reservationEnvironment.disableTimeRestriction()).thenReturn(true);
            when(user.getRoomNumber()).thenReturn(ROOM_NUMBER);
            // 만료된 RESERVED 예약은 쿼리 단계에서 제외되므로 활성 예약이 없는 것으로 조회된다
            when(reservationRepository.findCurrentlyActiveByMachine(machineWithExpiredReservation))
                    .thenReturn(List.of());
            // When & Then
            assertThatThrownBy(() -> createReservationService.execute(reqDto)).isInstanceOf(ExpectedException.class)
                    .hasMessageContaining("해당 기기를 사용할 수 없습니다").satisfies(
                            e -> assertThat(((ExpectedException) e).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
            verify(machineRepository, never()).save(machineWithExpiredReservation);
            verify(reservationRepository, never()).save(any(Reservation.class));
        }

        @Test
        @DisplayName("고장 기기에 만료된 RESERVED 예약만 남아 있어도 새 예약을 생성하지 않는다")
        void execute_ShouldThrowException_WhenUnavailableMachineOnlyHasExpiredReservation() {
            // Given
            when(currentUserProvider.getCurrentUserId()).thenReturn(USER_ID);
            final var reqDto = new CreateReservationReqDto(1L);
            var unavailableMachine = Machine.builder().name("세탁기 1").type(MachineType.WASHER)
                    .status(MachineStatus.MALFUNCTION).availability(MachineAvailability.UNAVAILABLE).build();

            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
            when(machineRepository.findById(reqDto.machineId())).thenReturn(Optional.of(unavailableMachine));
            when(penaltyRedisUtil.checkCooldown(eq(USER_ID), any())).thenReturn(RestrictionStatus.NONE);
            when(penaltyRedisUtil.checkBlock(ROOM_NUMBER)).thenReturn(RestrictionStatus.NONE);
            when(reservationEnvironment.disableTimeRestriction()).thenReturn(true);
            when(user.getRoomNumber()).thenReturn(ROOM_NUMBER);
            // 만료된 RESERVED 예약은 쿼리 단계에서 제외되므로 활성 예약이 없는 것으로 조회된다
            when(reservationRepository.findCurrentlyActiveByMachine(unavailableMachine)).thenReturn(List.of());

            // When & Then
            assertThatThrownBy(() -> createReservationService.execute(reqDto)).isInstanceOf(ExpectedException.class)
                    .hasMessageContaining("해당 기기를 사용할 수 없습니다");
            verify(reservationRepository, never()).saveAll(anyList());
            verify(machineRepository, never()).save(unavailableMachine);
        }

        @Test
        @DisplayName("세탁기와 건조기는 동시에 각 1개씩 예약할 수 있다")
        void execute_ShouldCreateReservation_WhenRoomHasDifferentTypeActiveReservation() {
            // Given
            when(currentUserProvider.getCurrentUserId()).thenReturn(USER_ID);
            final var reqDto = new CreateReservationReqDto(2L);

            when(userRepository.findByIdForUpdate(USER_ID)).thenReturn(Optional.of(user));
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
            when(machineRepository.findByIdForUpdate(reqDto.machineId())).thenReturn(Optional.of(machine));
            when(machineRepository.findById(reqDto.machineId())).thenReturn(Optional.of(machine));
            when(penaltyRedisUtil.checkCooldown(eq(USER_ID), any())).thenReturn(RestrictionStatus.NONE);
            when(penaltyRedisUtil.checkBlock(ROOM_NUMBER)).thenReturn(RestrictionStatus.NONE);
            when(reservationEnvironment.disableTimeRestriction()).thenReturn(true);
            when(machine.getAvailability()).thenReturn(MachineAvailability.AVAILABLE);
            when(user.getRoomNumber()).thenReturn(ROOM_NUMBER);
            when(machine.getType()).thenReturn(MachineType.DRYER);
            when(reservationRepository.findCurrentlyActiveByMachine(machine)).thenReturn(List.of());
            when(reservationRepository.findByUserAndStatusIn(eq(user), anyList())).thenReturn(List.of());
            when(reservationRepository.findByRoomNumberAndStatusIn(eq(ROOM_NUMBER), anyList())).thenReturn(List.of());
            when(reservationRepository.save(any(Reservation.class))).thenReturn(reservation);

            when(reservation.getId()).thenReturn(2L);
            when(reservation.getUser()).thenReturn(user);
            when(reservation.getMachine()).thenReturn(machine);
            when(user.getId()).thenReturn(USER_ID);
            when(machine.getId()).thenReturn(2L);

            // When
            final ReservationResDto result = createReservationService.execute(reqDto);

            // Then
            assertThat(result).isNotNull();
            verify(reservationRepository).save(any(Reservation.class));
        }

        @Test
        @DisplayName("쿨다운 중이면 예약이 제한된다")
        void execute_ShouldThrowException_WhenUserIsInCooldown() {
            // Given
            when(currentUserProvider.getCurrentUserId()).thenReturn(USER_ID);
            final var reqDto = new CreateReservationReqDto(1L);

            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
            when(user.getRoomNumber()).thenReturn(ROOM_NUMBER);
            when(penaltyRedisUtil.checkBlock(ROOM_NUMBER)).thenReturn(RestrictionStatus.NONE);
            when(reservationEnvironment.disableTimeRestriction()).thenReturn(true);
            when(machineRepository.findById(reqDto.machineId())).thenReturn(Optional.of(machine));
            when(machine.getType()).thenReturn(MachineType.WASHER);
            when(penaltyRedisUtil.checkCooldown(USER_ID, MachineType.WASHER)).thenReturn(RestrictionStatus.RESTRICTED);

            // When & Then
            assertThatThrownBy(() -> createReservationService.execute(reqDto)).isInstanceOf(ExpectedException.class)
                    .hasMessageContaining("5분간 세탁기 예약이 제한");
        }

        @Test
        @DisplayName("48시간 블록 중이면 예약이 제한된다")
        void execute_ShouldThrowException_WhenUserIsBlocked() {
            // Given
            when(currentUserProvider.getCurrentUserId()).thenReturn(USER_ID);
            final var reqDto = new CreateReservationReqDto(1L);

            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
            when(user.getRoomNumber()).thenReturn(ROOM_NUMBER);
            when(penaltyRedisUtil.checkBlock(ROOM_NUMBER)).thenReturn(RestrictionStatus.RESTRICTED);

            // When & Then
            assertThatThrownBy(() -> createReservationService.execute(reqDto)).isInstanceOf(ExpectedException.class)
                    .hasMessageContaining("48시간 내 취소 횟수를 초과");
        }

        @Test
        @DisplayName("호실 차단 조회에 실패하면 503 오류 코드로 예약을 거부한다")
        void execute_ShouldFailClosed_WhenBlockLookupFails() {
            // Given
            when(currentUserProvider.getCurrentUserId()).thenReturn(USER_ID);
            final var reqDto = new CreateReservationReqDto(1L);

            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
            when(user.getRoomNumber()).thenReturn(ROOM_NUMBER);
            when(penaltyRedisUtil.checkBlock(ROOM_NUMBER)).thenReturn(RestrictionStatus.UNAVAILABLE);

            // When & Then
            assertThatThrownBy(() -> createReservationService.execute(reqDto)).isInstanceOf(ErrorCodeException.class)
                    .extracting(e -> ((ErrorCodeException) e).getErrorCode())
                    .isEqualTo(ErrorCode.RESERVATION_RESTRICTION_UNAVAILABLE);
            verify(reservationRepository, never()).save(any());
        }

        @Test
        @DisplayName("쿨다운 조회에 실패하면 503 오류 코드로 예약을 거부한다")
        void execute_ShouldFailClosed_WhenCooldownLookupFails() {
            // Given
            when(currentUserProvider.getCurrentUserId()).thenReturn(USER_ID);
            final var reqDto = new CreateReservationReqDto(1L);

            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
            when(user.getRoomNumber()).thenReturn(ROOM_NUMBER);
            when(penaltyRedisUtil.checkBlock(ROOM_NUMBER)).thenReturn(RestrictionStatus.NONE);
            when(reservationEnvironment.disableTimeRestriction()).thenReturn(true);
            when(machineRepository.findById(reqDto.machineId())).thenReturn(Optional.of(machine));
            when(machine.getType()).thenReturn(MachineType.WASHER);
            when(penaltyRedisUtil.checkCooldown(USER_ID, MachineType.WASHER)).thenReturn(RestrictionStatus.UNAVAILABLE);

            // When & Then
            assertThatThrownBy(() -> createReservationService.execute(reqDto)).isInstanceOf(ErrorCodeException.class)
                    .extracting(e -> ((ErrorCodeException) e).getErrorCode())
                    .isEqualTo(ErrorCode.RESERVATION_RESTRICTION_UNAVAILABLE);
            verify(reservationRepository, never()).save(any());
        }

        @Test
        @DisplayName("기기가 사용 불가 상태이면 예외를 발생시킨다")
        void execute_ShouldThrowException_WhenMachineNotAvailable() {
            // Given
            when(currentUserProvider.getCurrentUserId()).thenReturn(USER_ID);
            final var reqDto = new CreateReservationReqDto(1L);

            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
            when(machineRepository.findById(reqDto.machineId())).thenReturn(Optional.of(machine));
            when(penaltyRedisUtil.checkCooldown(eq(USER_ID), any())).thenReturn(RestrictionStatus.NONE);
            when(user.getRoomNumber()).thenReturn(ROOM_NUMBER);
            when(penaltyRedisUtil.checkBlock(ROOM_NUMBER)).thenReturn(RestrictionStatus.NONE);
            when(reservationEnvironment.disableTimeRestriction()).thenReturn(true);
            when(machine.getAvailability()).thenReturn(MachineAvailability.IN_USE);
            when(machine.getName()).thenReturn("세탁기-1");
            when(reservationRepository.findCurrentlyActiveByMachine(machine)).thenReturn(List.of());

            // When & Then
            assertThatThrownBy(() -> createReservationService.execute(reqDto)).isInstanceOf(ExpectedException.class)
                    .hasMessageContaining("해당 기기를 사용할 수 없습니다");
        }

        @Test
        @DisplayName("기기에 이미 진행 중인 예약이 있으면 CONFLICT 예외를 발생시킨다")
        void execute_ShouldThrowConflict_WhenMachineAlreadyHasActiveReservation() {
            // Given
            when(currentUserProvider.getCurrentUserId()).thenReturn(USER_ID);
            final var reqDto = new CreateReservationReqDto(1L);

            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
            when(machineRepository.findById(reqDto.machineId())).thenReturn(Optional.of(machine));
            when(penaltyRedisUtil.checkCooldown(eq(USER_ID), any())).thenReturn(RestrictionStatus.NONE);
            when(user.getRoomNumber()).thenReturn(ROOM_NUMBER);
            when(penaltyRedisUtil.checkBlock(ROOM_NUMBER)).thenReturn(RestrictionStatus.NONE);
            when(reservationEnvironment.disableTimeRestriction()).thenReturn(true);
            when(machine.getAvailability()).thenReturn(MachineAvailability.AVAILABLE);
            when(machine.getName()).thenReturn("세탁기-1");
            when(reservationRepository.findCurrentlyActiveByMachine(machine)).thenReturn(List.of(reservation));

            // When & Then
            assertThatThrownBy(() -> createReservationService.execute(reqDto)).isInstanceOf(ExpectedException.class)
                    .hasMessageContaining("이미 진행 중인 예약")
                    .satisfies(e -> assertThat(((ExpectedException) e).getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
        }

        @Test
        @DisplayName("이미 활성 예약이 있는 개인이 추가 예약을 시도하면 예외를 발생시킨다")
        void execute_ShouldThrowException_WhenUserAlreadyHasActiveReservation() {
            // Given
            when(currentUserProvider.getCurrentUserId()).thenReturn(USER_ID);
            final var reqDto = new CreateReservationReqDto(1L);

            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
            when(machineRepository.findById(reqDto.machineId())).thenReturn(Optional.of(machine));
            when(penaltyRedisUtil.checkCooldown(eq(USER_ID), any())).thenReturn(RestrictionStatus.NONE);
            when(user.getRoomNumber()).thenReturn(ROOM_NUMBER);
            when(penaltyRedisUtil.checkBlock(ROOM_NUMBER)).thenReturn(RestrictionStatus.NONE);
            when(reservationEnvironment.disableTimeRestriction()).thenReturn(true);
            when(machine.getAvailability()).thenReturn(MachineAvailability.AVAILABLE);
            when(reservationRepository.findCurrentlyActiveByMachine(machine)).thenReturn(List.of());
            when(reservationRepository.findByUserAndStatusIn(eq(user), anyList())).thenReturn(List.of(reservation));

            // When & Then
            assertThatThrownBy(() -> createReservationService.execute(reqDto)).isInstanceOf(ExpectedException.class)
                    .hasMessageContaining("1인 1예약");
        }

        @Test
        @DisplayName("금지된 호실의 사용자가 예약을 시도하면 FORBIDDEN 예외를 발생시킨다")
        void execute_ShouldThrowForbidden_WhenRoomIsBanned() {
            // Given
            when(currentUserProvider.getCurrentUserId()).thenReturn(USER_ID);
            final var reqDto = new CreateReservationReqDto(1L);

            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
            when(user.getRoomNumber()).thenReturn(ROOM_NUMBER);
            when(washingBanRepository.existsByRoomNumber(ROOM_NUMBER)).thenReturn(true);

            // When & Then
            assertThatThrownBy(() -> createReservationService.execute(reqDto)).isInstanceOf(ExpectedException.class)
                    .hasMessageContaining("세탁이 금지된").satisfies(
                            e -> assertThat(((ExpectedException) e).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
        }

        @Test
        @DisplayName("5층(여학생) 사용자가 예약을 시도하면 예외를 발생시킨다")
        void execute_ShouldThrowException_WhenUserIsOnFemaleFloor() {
            // Given
            when(currentUserProvider.getCurrentUserId()).thenReturn(USER_ID);
            final var reqDto = new CreateReservationReqDto(1L);

            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
            doThrow(new ExpectedException("1~4층 기숙사생이 아니라면 서비스를 이용할 수 없습니다.", HttpStatus.UNAVAILABLE_FOR_LEGAL_REASONS))
                    .when(user).validateFloorRestriction();

            // When & Then
            assertThatThrownBy(() -> createReservationService.execute(reqDto)).isInstanceOf(ExpectedException.class)
                    .hasMessage("1~4층 기숙사생이 아니라면 서비스를 이용할 수 없습니다.")
                    .satisfies(e -> assertThat(((ExpectedException) e).getStatusCode())
                            .isEqualTo(HttpStatus.UNAVAILABLE_FOR_LEGAL_REASONS));

            verify(machineRepository, never()).findByIdForUpdate(any());
        }

        @Test
        @DisplayName("동일 호실에 같은 유형의 활성 예약이 있으면 예외를 발생시킨다")
        void execute_ShouldThrowException_WhenRoomAlreadyHasSameTypeActiveReservation() {
            // Given
            when(currentUserProvider.getCurrentUserId()).thenReturn(USER_ID);
            final var reqDto = new CreateReservationReqDto(1L);

            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
            when(machineRepository.findById(reqDto.machineId())).thenReturn(Optional.of(machine));
            when(penaltyRedisUtil.checkCooldown(eq(USER_ID), any())).thenReturn(RestrictionStatus.NONE);
            when(penaltyRedisUtil.checkBlock(ROOM_NUMBER)).thenReturn(RestrictionStatus.NONE);
            when(reservationEnvironment.disableTimeRestriction()).thenReturn(true);
            when(machine.getAvailability()).thenReturn(MachineAvailability.AVAILABLE);
            when(user.getRoomNumber()).thenReturn(ROOM_NUMBER);
            when(machine.getType()).thenReturn(MachineType.WASHER);
            when(reservationRepository.findCurrentlyActiveByMachine(machine)).thenReturn(List.of());
            when(reservationRepository.findByUserAndStatusIn(eq(user), anyList())).thenReturn(List.of());
            when(reservationRepository.findByRoomNumberAndStatusIn(eq(ROOM_NUMBER), anyList()))
                    .thenReturn(List.of(reservation));
            when(reservation.getMachine()).thenReturn(machine);

            // When & Then
            assertThatThrownBy(() -> createReservationService.execute(reqDto)).isInstanceOf(ExpectedException.class)
                    .hasMessageContaining("세탁기");
        }
    }

    @Nested
    @DisplayName("SmartThings 작동 상태 검증")
    class DeviceStateVerificationTest {

        private CreateReservationReqDto givenPreValidatedRequest() {
            when(currentUserProvider.getCurrentUserId()).thenReturn(USER_ID);
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
            when(user.getRoomNumber()).thenReturn(ROOM_NUMBER);
            when(penaltyRedisUtil.checkBlock(ROOM_NUMBER)).thenReturn(RestrictionStatus.NONE);
            when(reservationEnvironment.disableTimeRestriction()).thenReturn(true);
            when(machineRepository.findById(1L)).thenReturn(Optional.of(machine));
            when(machine.getType()).thenReturn(MachineType.WASHER);
            when(penaltyRedisUtil.checkCooldown(USER_ID, MachineType.WASHER)).thenReturn(RestrictionStatus.NONE);
            when(machine.getAvailability()).thenReturn(MachineAvailability.AVAILABLE);
            // 기기 단위 검증에서 먼저 실패하는 경우에는 조회되지 않는다
            lenient().when(reservationRepository.findByUserAndStatusIn(eq(user), anyList())).thenReturn(List.of());
            lenient().when(reservationRepository.findByRoomNumberAndStatusIn(eq(ROOM_NUMBER), anyList()))
                    .thenReturn(List.of());
            return new CreateReservationReqDto(1L);
        }

        @Test
        @DisplayName("기기가 작동 중이면 락을 잡지 않고 예약을 거부한다")
        void execute_ShouldRejectWithoutLock_WhenMachineIsOperating() {
            // Given
            final var reqDto = givenPreValidatedRequest();
            when(reservationRepository.findCurrentlyActiveByMachine(machine)).thenReturn(List.of());
            doThrow(new ExpectedException("해당 기기가 현재 작동 중이어서 예약할 수 없습니다. 기기: 세탁기-1", HttpStatus.CONFLICT))
                    .when(reservationDeviceStateVerifier).verifyNotOperating(machine);

            // When & Then
            assertThatThrownBy(() -> createReservationService.execute(reqDto)).isInstanceOf(ExpectedException.class)
                    .hasMessageContaining("작동 중")
                    .satisfies(e -> assertThat(((ExpectedException) e).getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
            verify(userRepository, never()).findRoomUserIdsByUserIdForUpdate(any());
            verify(userRepository, never()).findByIdForUpdate(any());
            verify(machineRepository, never()).findByIdForUpdate(any());
            verify(reservationRepository, never()).save(any(Reservation.class));
        }

        @Test
        @DisplayName("기기 상태를 확인할 수 없으면 MACHINE_STATE_UNAVAILABLE 오류 코드로 예약을 거부한다")
        void execute_ShouldRejectWithErrorCode_WhenDeviceStateUnknown() {
            // Given
            final var reqDto = givenPreValidatedRequest();
            when(reservationRepository.findCurrentlyActiveByMachine(machine)).thenReturn(List.of());
            doThrow(new ErrorCodeException(ErrorCode.MACHINE_STATE_UNAVAILABLE)).when(reservationDeviceStateVerifier)
                    .verifyNotOperating(machine);

            // When & Then
            assertThatThrownBy(() -> createReservationService.execute(reqDto)).isInstanceOf(ErrorCodeException.class)
                    .extracting(e -> ((ErrorCodeException) e).getErrorCode())
                    .isEqualTo(ErrorCode.MACHINE_STATE_UNAVAILABLE);
            verify(machineRepository, never()).findByIdForUpdate(any());
            verify(reservationRepository, never()).save(any(Reservation.class));
        }

        @Test
        @DisplayName("사전 검증에 실패하면 SmartThings 상태를 조회하지 않는다")
        void execute_ShouldNotQueryDeviceState_WhenPreValidationFails() {
            // Given
            final var reqDto = givenPreValidatedRequest();
            when(machine.getName()).thenReturn("세탁기-1");
            when(reservationRepository.findCurrentlyActiveByMachine(machine)).thenReturn(List.of(reservation));

            // When & Then
            assertThatThrownBy(() -> createReservationService.execute(reqDto)).isInstanceOf(ExpectedException.class)
                    .hasMessageContaining("이미 진행 중인 예약");
            verify(reservationDeviceStateVerifier, never()).verifyNotOperating(any());
        }

        @Test
        @DisplayName("상태 확인은 트랜잭션 밖에서 락 획득 전에 수행되고, 락 획득 후 불변식을 다시 확인한 뒤 저장한다")
        void execute_ShouldVerifyDeviceStateBeforeLock_AndRevalidateUnderLock() {
            // Given
            final var reqDto = givenPreValidatedRequest();
            when(reservationRepository.findCurrentlyActiveByMachine(machine)).thenReturn(List.of());
            when(userRepository.findByIdForUpdate(USER_ID)).thenReturn(Optional.of(user));
            when(machineRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(machine));
            when(reservationRepository.save(any(Reservation.class))).thenReturn(reservation);
            when(reservation.getUser()).thenReturn(user);
            when(reservation.getMachine()).thenReturn(machine);

            // When
            createReservationService.execute(reqDto);

            // Then
            final InOrder inOrder = inOrder(transactionManager,
                    userRepository,
                    machineRepository,
                    reservationDeviceStateVerifier,
                    reservationRepository);
            inOrder.verify(transactionManager).getTransaction(argThat(
                    definition -> definition.getIsolationLevel() == TransactionDefinition.ISOLATION_READ_COMMITTED));
            inOrder.verify(machineRepository).findById(1L);
            inOrder.verify(transactionManager).commit(any());
            inOrder.verify(reservationDeviceStateVerifier).verifyNotOperating(machine);
            inOrder.verify(transactionManager).getTransaction(argThat(
                    definition -> definition.getIsolationLevel() == TransactionDefinition.ISOLATION_READ_COMMITTED));
            inOrder.verify(userRepository).findRoomUserIdsByUserIdForUpdate(USER_ID);
            inOrder.verify(userRepository).findByIdForUpdate(USER_ID);
            inOrder.verify(machineRepository).findByIdForUpdate(1L);
            inOrder.verify(reservationRepository).findCurrentlyActiveByMachine(machine);
            inOrder.verify(reservationRepository).save(any(Reservation.class));
            inOrder.verify(transactionManager).commit(any());
            verify(reservationRepository, times(2)).findCurrentlyActiveByMachine(machine);
        }

        @Test
        @DisplayName("상태 확인과 락 사이에 다른 예약이 생기면 락 아래 재검증에서 CONFLICT로 거부한다")
        void execute_ShouldThrowConflict_WhenReservationCreatedBetweenVerificationAndLock() {
            // Given
            final var reqDto = givenPreValidatedRequest();
            when(machine.getName()).thenReturn("세탁기-1");
            when(userRepository.findByIdForUpdate(USER_ID)).thenReturn(Optional.of(user));
            when(machineRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(machine));
            // 사전 검증 시에는 비어 있었지만 외부 조회 중 다른 요청이 먼저 예약을 저장한 상황
            when(reservationRepository.findCurrentlyActiveByMachine(machine)).thenReturn(List.of())
                    .thenReturn(List.of(reservation));

            // When & Then
            assertThatThrownBy(() -> createReservationService.execute(reqDto)).isInstanceOf(ExpectedException.class)
                    .hasMessageContaining("이미 진행 중인 예약")
                    .satisfies(e -> assertThat(((ExpectedException) e).getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
            // 락 아래에서는 외부 상태를 다시 조회하지 않으므로 확인 직후의 기기 상태 변화는 감지 범위 밖이다
            verify(reservationDeviceStateVerifier, times(1)).verifyNotOperating(machine);
            verify(transactionManager).rollback(any());
            verify(reservationRepository, never()).save(any(Reservation.class));
        }
    }
}
