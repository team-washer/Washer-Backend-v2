package team.washer.server.v2.domain.reservation.service.impl;

import java.time.LocalDateTime;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import team.washer.server.v2.domain.machine.entity.Machine;
import team.washer.server.v2.domain.machine.repository.MachineRepository;
import team.washer.server.v2.domain.reservation.dto.request.CreateReservationReqDto;
import team.washer.server.v2.domain.reservation.dto.response.ReservationResDto;
import team.washer.server.v2.domain.reservation.entity.Reservation;
import team.washer.server.v2.domain.reservation.enums.ReservationStatus;
import team.washer.server.v2.domain.reservation.repository.ReservationRepository;
import team.washer.server.v2.domain.reservation.service.CreateReservationService;
import team.washer.server.v2.domain.reservation.service.ValidateFutureTimeService;
import team.washer.server.v2.domain.reservation.service.ValidateMachineAvailabilityService;
import team.washer.server.v2.domain.reservation.service.ValidateTimeRestrictionService;
import team.washer.server.v2.domain.reservation.service.ValidateUserNotPenalizedService;
import team.washer.server.v2.domain.user.entity.User;
import team.washer.server.v2.domain.user.repository.UserRepository;
import team.washer.server.v2.global.common.error.exception.ExpectedException;

@Slf4j
@Service
@RequiredArgsConstructor
public class CreateReservationServiceImpl implements CreateReservationService {

    private final ReservationRepository reservationRepository;
    private final UserRepository userRepository;
    private final MachineRepository machineRepository;
    private final ValidateFutureTimeService validateFutureTimeService;
    private final ValidateTimeRestrictionService validateTimeRestrictionService;
    private final ValidateUserNotPenalizedService validateUserNotPenalizedService;
    private final ValidateMachineAvailabilityService validateMachineAvailabilityService;

    @Override
    @Transactional
    public ReservationResDto execute(final Long userId, final CreateReservationReqDto reqDto) {
        final User user = userRepository.findById(userId)
                .orElseThrow(() -> new ExpectedException("사용자를 찾을 수 없습니다", HttpStatus.NOT_FOUND));

        final Machine machine = machineRepository.findById(reqDto.machineId())
                .orElseThrow(() -> new ExpectedException("기기를 찾을 수 없습니다", HttpStatus.NOT_FOUND));

        validateFutureTimeService.execute(reqDto.startTime());
        validateTimeRestrictionService.execute(user, reqDto.startTime());
        validateUserNotPenalizedService.execute(userId);

        final LocalDateTime expectedCompletionTime = reqDto.startTime().plusMinutes(90);

        validateMachineAvailabilityService.execute(machine, reqDto.startTime(), expectedCompletionTime, null);

        final Reservation reservation = Reservation.builder().user(user).machine(machine)
                .reservedAt(LocalDateTime.now()).startTime(reqDto.startTime())
                .dayOfWeek(reqDto.startTime().getDayOfWeek()).status(ReservationStatus.RESERVED).build();

        final Reservation saved = reservationRepository.save(reservation);
        log.info("Created reservation {} for user {} on machine {}", saved.getId(), userId, machine.getId());

        return mapToReservationResDto(saved);
    }

    private ReservationResDto mapToReservationResDto(final Reservation reservation) {
        return new ReservationResDto(reservation.getId(),
                reservation.getUser().getId(),
                reservation.getUser().getName(),
                reservation.getUser().getRoomNumber(),
                reservation.getMachine().getId(),
                reservation.getMachine().getName(),
                reservation.getReservedAt(),
                reservation.getStartTime(),
                reservation.getExpectedCompletionTime(),
                reservation.getActualCompletionTime(),
                reservation.getStatus(),
                reservation.getConfirmedAt(),
                reservation.getCancelledAt(),
                reservation.getDayOfWeek(),
                reservation.getCreatedAt(),
                reservation.getUpdatedAt());
    }
}
