package team.washer.server.v2.domain.reservation.service.impl;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import team.washer.server.v2.domain.reservation.dto.response.ReservationResDto;
import team.washer.server.v2.domain.reservation.entity.Reservation;
import team.washer.server.v2.domain.reservation.repository.ReservationRepository;
import team.washer.server.v2.domain.reservation.service.QueryReservationService;
import team.washer.server.v2.global.common.error.code.ErrorCode;
import team.washer.server.v2.global.common.error.exception.ErrorCodeException;

@Service
@RequiredArgsConstructor
public class QueryReservationServiceImpl implements QueryReservationService {

    private final ReservationRepository reservationRepository;

    @Override
    @Transactional(readOnly = true)
    public ReservationResDto execute(final Long reservationId) {
        final Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new ErrorCodeException(ErrorCode.RESERVATION_NOT_FOUND));

        return new ReservationResDto(reservation.getId(),
                reservation.getUser().getId(),
                reservation.getUser().getName(),
                reservation.getUser().getRoomNumber(),
                reservation.getUser().getStudentId(),
                reservation.getMachine().getId(),
                reservation.getMachine().getName(),
                reservation.getReservedAt(),
                reservation.getStartTime(),
                reservation.getExpectedCompletionTime(),
                reservation.getActualCompletionTime(),
                reservation.getStatus(),
                reservation.getCancelledAt(),
                reservation.getDayOfWeek(),
                reservation.getCreatedAt(),
                reservation.getUpdatedAt());
    }
}
