package team.washer.server.v2.domain.reservation.service.impl;

import java.time.LocalDateTime;

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
import team.washer.server.v2.domain.reservation.service.ReservationValidationService;
import team.washer.server.v2.domain.user.entity.User;
import team.washer.server.v2.domain.user.repository.UserRepository;

@Slf4j
@Service
@RequiredArgsConstructor
public class CreateReservationServiceImpl implements CreateReservationService {

    private final ReservationRepository reservationRepository;
    private final UserRepository userRepository;
    private final MachineRepository machineRepository;
    private final ReservationValidationService validationService;

    @Override
    @Transactional
    public ReservationResDto createReservation(final Long userId, final CreateReservationReqDto reqDto) {
        final User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다: " + userId));

        final Machine machine = machineRepository.findById(reqDto.machineId())
                .orElseThrow(() -> new IllegalArgumentException("기기를 찾을 수 없습니다: " + reqDto.machineId()));

        validationService.validateFutureTime(reqDto.startTime());
        validationService.validateTimeRestriction(user, reqDto.startTime());
        validationService.validateUserNotPenalized(userId);

        final LocalDateTime expectedCompletionTime = reqDto.startTime().plusMinutes(90);

        validationService.validateMachineAvailable(machine, reqDto.startTime(), expectedCompletionTime, null);

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
