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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import team.themoment.sdk.exception.ExpectedException;
import team.washer.server.v2.domain.admin.repository.WashingBanRepository;
import team.washer.server.v2.domain.machine.entity.Machine;
import team.washer.server.v2.domain.machine.enums.MachineAvailability;
import team.washer.server.v2.domain.machine.enums.MachineType;
import team.washer.server.v2.domain.machine.repository.MachineRepository;
import team.washer.server.v2.domain.reservation.dto.request.AdminCreateReservationReqDto;
import team.washer.server.v2.domain.reservation.entity.Reservation;
import team.washer.server.v2.domain.reservation.repository.ReservationRepository;
import team.washer.server.v2.domain.reservation.service.impl.AdminCreateReservationServiceImpl;
import team.washer.server.v2.domain.reservation.support.ReservationCreationSupport;
import team.washer.server.v2.domain.user.entity.User;
import team.washer.server.v2.domain.user.repository.UserRepository;
import team.washer.server.v2.global.security.provider.CurrentUserProvider;

@ExtendWith(MockitoExtension.class)
class AdminCreateReservationServiceTest {

    private AdminCreateReservationServiceImpl adminCreateReservationService;

    @Mock
    private ReservationRepository reservationRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private MachineRepository machineRepository;
    @Mock
    private WashingBanRepository washingBanRepository;
    @Mock
    private CurrentUserProvider currentUserProvider;

    @Mock
    private User targetUser;
    @Mock
    private User adminUser;
    @Mock
    private Machine machine;
    @Mock
    private Reservation activeReservation;

    private static final Long TARGET_USER_ID = 12L;
    private static final Long ADMIN_ID = 1L;
    private static final Long MACHINE_ID = 3L;
    private static final String ROOM_NUMBER = "301";
    private static final String ADMIN_NAME = "박관리";

    // 불변식 검증 순서와 에러 메시지를 그대로 검증하기 위해 Support는 실제 구현체를 사용한다
    @BeforeEach
    void setUp() {
        final var reservationCreationSupport = new ReservationCreationSupport(reservationRepository,
                machineRepository,
                washingBanRepository);
        adminCreateReservationService = new AdminCreateReservationServiceImpl(userRepository,
                currentUserProvider,
                reservationCreationSupport);
    }

    private AdminCreateReservationReqDto givenValidRequest() {
        when(userRepository.findById(TARGET_USER_ID)).thenReturn(Optional.of(targetUser));
        when(currentUserProvider.getCurrentUserId()).thenReturn(ADMIN_ID);
        when(userRepository.findById(ADMIN_ID)).thenReturn(Optional.of(adminUser));
        when(targetUser.getRoomNumber()).thenReturn(ROOM_NUMBER);
        when(machineRepository.findByIdForUpdate(MACHINE_ID)).thenReturn(Optional.of(machine));
        when(machine.getAvailability()).thenReturn(MachineAvailability.AVAILABLE);
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        return new AdminCreateReservationReqDto(TARGET_USER_ID, MACHINE_ID);
    }

    @Nested
    @DisplayName("관리자 대리 예약 생성")
    class ExecuteTest {

        @Test
        @DisplayName("관리자가 지정한 사용자 명의로 예약이 생성된다")
        void execute_ShouldCreateReservationForTargetUser() {
            // Given
            final var reqDto = givenValidRequest();

            // When
            final var result = adminCreateReservationService.execute(reqDto);

            // Then
            final var captor = ArgumentCaptor.forClass(Reservation.class);
            verify(reservationRepository).save(captor.capture());
            assertThat(captor.getValue().getUser()).isSameAs(targetUser);
            assertThat(result).isNotNull();
            verify(machine).markAsReserved();
        }

        @Test
        @DisplayName("생성된 예약의 createdBy에 관리자가 기록된다")
        void execute_ShouldRecordAdminAsCreatedBy() {
            // Given
            final var reqDto = givenValidRequest();
            when(adminUser.getName()).thenReturn(ADMIN_NAME);

            // When
            final var result = adminCreateReservationService.execute(reqDto);

            // Then
            final var captor = ArgumentCaptor.forClass(Reservation.class);
            verify(reservationRepository).save(captor.capture());
            assertThat(captor.getValue().getCreatedBy()).isSameAs(adminUser);
            assertThat(captor.getValue().isProxyReservation()).isTrue();
            assertThat(result.createdByAdminName()).isEqualTo(ADMIN_NAME);
        }

        @Test
        @DisplayName("시간 제한 시간대에도 대리 예약이 생성된다")
        void execute_ShouldBypassTimeRestriction() {
            // Given
            final var reqDto = givenValidRequest();

            // When
            adminCreateReservationService.execute(reqDto);

            // Then
            verify(targetUser, never()).validateTimeRestriction(any());
            verify(reservationRepository).save(any(Reservation.class));
        }

        @Test
        @DisplayName("존재하지 않는 사용자면 NOT_FOUND 예외를 발생시킨다")
        void execute_ShouldThrowNotFound_WhenTargetUserNotFound() {
            // Given
            final var reqDto = new AdminCreateReservationReqDto(TARGET_USER_ID, MACHINE_ID);
            when(userRepository.findById(TARGET_USER_ID)).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> adminCreateReservationService.execute(reqDto))
                    .isInstanceOf(ExpectedException.class).hasMessageContaining("사용자를 찾을 수 없습니다").satisfies(
                            e -> assertThat(((ExpectedException) e).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
        }

        @Test
        @DisplayName("존재하지 않는 기기면 NOT_FOUND 예외를 발생시킨다")
        void execute_ShouldThrowNotFound_WhenMachineNotFound() {
            // Given
            final var reqDto = new AdminCreateReservationReqDto(TARGET_USER_ID, MACHINE_ID);
            when(userRepository.findById(TARGET_USER_ID)).thenReturn(Optional.of(targetUser));
            when(currentUserProvider.getCurrentUserId()).thenReturn(ADMIN_ID);
            when(userRepository.findById(ADMIN_ID)).thenReturn(Optional.of(adminUser));
            when(targetUser.getRoomNumber()).thenReturn(ROOM_NUMBER);
            when(machineRepository.findByIdForUpdate(MACHINE_ID)).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> adminCreateReservationService.execute(reqDto))
                    .isInstanceOf(ExpectedException.class).hasMessageContaining("기기를 찾을 수 없습니다").satisfies(
                            e -> assertThat(((ExpectedException) e).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
        }

        @Test
        @DisplayName("세탁이 금지된 호실이면 FORBIDDEN 예외를 발생시킨다")
        void execute_ShouldThrowForbidden_WhenRoomIsBanned() {
            // Given
            final var reqDto = new AdminCreateReservationReqDto(TARGET_USER_ID, MACHINE_ID);
            when(userRepository.findById(TARGET_USER_ID)).thenReturn(Optional.of(targetUser));
            when(currentUserProvider.getCurrentUserId()).thenReturn(ADMIN_ID);
            when(userRepository.findById(ADMIN_ID)).thenReturn(Optional.of(adminUser));
            when(targetUser.getRoomNumber()).thenReturn(ROOM_NUMBER);
            when(washingBanRepository.existsByRoomNumber(ROOM_NUMBER)).thenReturn(true);

            // When & Then
            assertThatThrownBy(() -> adminCreateReservationService.execute(reqDto))
                    .isInstanceOf(ExpectedException.class).hasMessageContaining("세탁이 금지된").satisfies(
                            e -> assertThat(((ExpectedException) e).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
        }

        @Test
        @DisplayName("기기가 사용 불가 상태이면 BAD_REQUEST 예외를 발생시킨다")
        void execute_ShouldThrowBadRequest_WhenMachineNotAvailable() {
            // Given
            final var reqDto = new AdminCreateReservationReqDto(TARGET_USER_ID, MACHINE_ID);
            when(userRepository.findById(TARGET_USER_ID)).thenReturn(Optional.of(targetUser));
            when(currentUserProvider.getCurrentUserId()).thenReturn(ADMIN_ID);
            when(userRepository.findById(ADMIN_ID)).thenReturn(Optional.of(adminUser));
            when(targetUser.getRoomNumber()).thenReturn(ROOM_NUMBER);
            when(machineRepository.findByIdForUpdate(MACHINE_ID)).thenReturn(Optional.of(machine));
            when(machine.getAvailability()).thenReturn(MachineAvailability.IN_USE);

            // When & Then
            assertThatThrownBy(() -> adminCreateReservationService.execute(reqDto))
                    .isInstanceOf(ExpectedException.class).hasMessageContaining("해당 기기를 사용할 수 없습니다").satisfies(
                            e -> assertThat(((ExpectedException) e).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        }

        @Test
        @DisplayName("기기에 이미 진행 중인 예약이 있으면 CONFLICT 예외를 발생시킨다")
        void execute_ShouldThrowConflict_WhenMachineHasActiveReservation() {
            // Given
            final var reqDto = new AdminCreateReservationReqDto(TARGET_USER_ID, MACHINE_ID);
            when(userRepository.findById(TARGET_USER_ID)).thenReturn(Optional.of(targetUser));
            when(currentUserProvider.getCurrentUserId()).thenReturn(ADMIN_ID);
            when(userRepository.findById(ADMIN_ID)).thenReturn(Optional.of(adminUser));
            when(targetUser.getRoomNumber()).thenReturn(ROOM_NUMBER);
            when(machineRepository.findByIdForUpdate(MACHINE_ID)).thenReturn(Optional.of(machine));
            when(machine.getAvailability()).thenReturn(MachineAvailability.AVAILABLE);
            when(activeReservation.isActive()).thenReturn(true);
            when(reservationRepository.findByMachineAndStatusIn(eq(machine), any()))
                    .thenReturn(List.of(activeReservation));

            // When & Then
            assertThatThrownBy(() -> adminCreateReservationService.execute(reqDto))
                    .isInstanceOf(ExpectedException.class).hasMessageContaining("이미 진행 중인 예약이 있습니다")
                    .satisfies(e -> assertThat(((ExpectedException) e).getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
        }

        @Test
        @DisplayName("대상 사용자에게 이미 활성 예약이 있으면 예외를 발생시킨다")
        void execute_ShouldThrowBadRequest_WhenTargetUserHasActiveReservation() {
            // Given
            final var reqDto = new AdminCreateReservationReqDto(TARGET_USER_ID, MACHINE_ID);
            when(userRepository.findById(TARGET_USER_ID)).thenReturn(Optional.of(targetUser));
            when(currentUserProvider.getCurrentUserId()).thenReturn(ADMIN_ID);
            when(userRepository.findById(ADMIN_ID)).thenReturn(Optional.of(adminUser));
            when(targetUser.getRoomNumber()).thenReturn(ROOM_NUMBER);
            when(machineRepository.findByIdForUpdate(MACHINE_ID)).thenReturn(Optional.of(machine));
            when(machine.getAvailability()).thenReturn(MachineAvailability.AVAILABLE);
            when(activeReservation.isActive()).thenReturn(true);
            when(reservationRepository.findByUserAndStatusIn(eq(targetUser), any()))
                    .thenReturn(List.of(activeReservation));

            // When & Then
            assertThatThrownBy(() -> adminCreateReservationService.execute(reqDto))
                    .isInstanceOf(ExpectedException.class).hasMessageContaining("1인 1예약만 가능합니다");
        }

        @Test
        @DisplayName("호실에 동일 유형의 활성 예약이 있으면 예외를 발생시킨다")
        void execute_ShouldThrowBadRequest_WhenRoomHasSameTypeReservation() {
            // Given
            final var reqDto = new AdminCreateReservationReqDto(TARGET_USER_ID, MACHINE_ID);
            when(userRepository.findById(TARGET_USER_ID)).thenReturn(Optional.of(targetUser));
            when(currentUserProvider.getCurrentUserId()).thenReturn(ADMIN_ID);
            when(userRepository.findById(ADMIN_ID)).thenReturn(Optional.of(adminUser));
            when(targetUser.getRoomNumber()).thenReturn(ROOM_NUMBER);
            when(machineRepository.findByIdForUpdate(MACHINE_ID)).thenReturn(Optional.of(machine));
            when(machine.getAvailability()).thenReturn(MachineAvailability.AVAILABLE);
            when(machine.getType()).thenReturn(MachineType.WASHER);
            when(activeReservation.getMachine()).thenReturn(machine);
            when(activeReservation.isActive()).thenReturn(true);
            when(reservationRepository.findActiveReservationsByRoomNumber(ROOM_NUMBER))
                    .thenReturn(List.of(activeReservation));

            // When & Then
            assertThatThrownBy(() -> adminCreateReservationService.execute(reqDto))
                    .isInstanceOf(ExpectedException.class).hasMessageContaining("동일 유형의 기기는 동시에 두 개 이상 예약할 수 없습니다");
        }
    }
}
