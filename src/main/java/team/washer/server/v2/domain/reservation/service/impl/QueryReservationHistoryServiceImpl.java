package team.washer.server.v2.domain.reservation.service.impl;

import java.time.LocalDateTime;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import team.washer.server.v2.domain.machine.enums.MachineType;
import team.washer.server.v2.domain.reservation.dto.response.ReservationHistoryPageResDto;
import team.washer.server.v2.domain.reservation.dto.response.ReservationHistoryResDto;
import team.washer.server.v2.domain.reservation.entity.Reservation;
import team.washer.server.v2.domain.reservation.enums.ReservationStatus;
import team.washer.server.v2.domain.reservation.repository.ReservationRepository;
import team.washer.server.v2.domain.reservation.service.QueryReservationHistoryService;

@Service
@RequiredArgsConstructor
public class QueryReservationHistoryServiceImpl implements QueryReservationHistoryService {

    private final ReservationRepository reservationRepository;

    @Override
    @Transactional(readOnly = true)
    public ReservationHistoryPageResDto queryReservationHistory(final Long userId,
            final ReservationStatus status,
            final LocalDateTime startDate,
            final LocalDateTime endDate,
            final MachineType machineType,
            final Pageable pageable) {

        final Page<Reservation> reservations = reservationRepository
                .findReservationHistory(userId, status, startDate, endDate, machineType, pageable);

        return new ReservationHistoryPageResDto(
                reservations.getContent().stream().map(this::mapToReservationHistoryResDto).collect(Collectors.toList()),
                reservations.getNumber(),
                reservations.getSize(),
                reservations.getTotalElements(),
                reservations.getTotalPages(),
                reservations.isLast());
    }

    private ReservationHistoryResDto mapToReservationHistoryResDto(final Reservation reservation) {
        final LocalDateTime completionTime = reservation.getActualCompletionTime() != null
                ? reservation.getActualCompletionTime()
                : reservation.getCancelledAt();

        return new ReservationHistoryResDto(reservation.getId(),
                reservation.getUser().getRoomNumber(),
                reservation.getMachine().getName(),
                reservation.getMachine().getType(),
                reservation.getStartTime(),
                completionTime,
                reservation.getStatus(),
                reservation.getCreatedAt());
    }
}
