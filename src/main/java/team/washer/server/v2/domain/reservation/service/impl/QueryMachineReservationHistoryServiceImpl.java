package team.washer.server.v2.domain.reservation.service.impl;

import java.time.LocalDateTime;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import team.themoment.sdk.exception.ExpectedException;
import team.washer.server.v2.domain.machine.repository.MachineRepository;
import team.washer.server.v2.domain.reservation.dto.response.MachineReservationHistoryPageResDto;
import team.washer.server.v2.domain.reservation.dto.response.MachineReservationHistoryResDto;
import team.washer.server.v2.domain.reservation.entity.Reservation;
import team.washer.server.v2.domain.reservation.enums.ReservationStatus;
import team.washer.server.v2.domain.reservation.repository.ReservationRepository;
import team.washer.server.v2.domain.reservation.service.QueryMachineReservationHistoryService;

@Service
@RequiredArgsConstructor
public class QueryMachineReservationHistoryServiceImpl implements QueryMachineReservationHistoryService {

    private final ReservationRepository reservationRepository;
    private final MachineRepository machineRepository;

    @Override
    @Transactional(readOnly = true)
    public MachineReservationHistoryPageResDto execute(final Long machineId,
            final ReservationStatus status,
            final LocalDateTime startDate,
            final LocalDateTime endDate,
            final Pageable pageable) {

        if (!machineRepository.existsById(machineId)) {
            throw new ExpectedException("존재하지 않는 기기입니다", HttpStatus.NOT_FOUND);
        }

        final Page<Reservation> reservations = reservationRepository
                .findMachineReservationHistory(machineId, status, startDate, endDate, pageable);

        return new MachineReservationHistoryPageResDto(
                reservations.getContent().stream().map(this::mapToResDto).collect(Collectors.toList()),
                reservations.getNumber(),
                reservations.getSize(),
                reservations.getTotalElements(),
                reservations.getTotalPages(),
                reservations.isLast());
    }

    private MachineReservationHistoryResDto mapToResDto(final Reservation reservation) {
        final LocalDateTime completionTime = reservation.getActualCompletionTime() != null
                ? reservation.getActualCompletionTime()
                : reservation.getCancelledAt();

        return new MachineReservationHistoryResDto(reservation.getId(),
                reservation.getUser().getRoomNumber(),
                reservation.getStartTime(),
                completionTime,
                reservation.getStatus(),
                reservation.getCreatedAt());
    }
}
