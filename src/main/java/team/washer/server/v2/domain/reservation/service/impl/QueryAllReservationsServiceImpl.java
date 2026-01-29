package team.washer.server.v2.domain.reservation.service.impl;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import team.washer.server.v2.domain.reservation.dto.response.AdminReservationListResDto;
import team.washer.server.v2.domain.reservation.dto.response.AdminReservationResDto;
import team.washer.server.v2.domain.reservation.entity.Reservation;
import team.washer.server.v2.domain.reservation.enums.ReservationStatus;
import team.washer.server.v2.domain.reservation.repository.ReservationRepository;
import team.washer.server.v2.domain.reservation.service.QueryAllReservationsService;

@Service
@RequiredArgsConstructor
public class QueryAllReservationsServiceImpl implements QueryAllReservationsService {

    private final ReservationRepository reservationRepository;

    @Transactional(readOnly = true)
    @Override
    public AdminReservationListResDto execute(String userName,
            String machineName,
            ReservationStatus status,
            LocalDateTime startDate,
            LocalDateTime endDate) {

        final var reservations = reservationRepository
                .findAllWithFilters(userName, machineName, status, startDate, endDate);
        final var reservationDtos = reservations.stream().map(this::toAdminReservationResDto).toList();

        return new AdminReservationListResDto(reservationDtos, reservationDtos.size());
    }

    private AdminReservationResDto toAdminReservationResDto(Reservation reservation) {
        return new AdminReservationResDto(reservation.getId(),
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
                reservation.getCancelledAt());
    }
}
