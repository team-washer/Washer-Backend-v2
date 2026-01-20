package team.washer.server.v2.domain.reservation.service.impl;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import team.washer.server.v2.domain.reservation.dto.response.ReservationResDto;
import team.washer.server.v2.domain.reservation.entity.Reservation;
import team.washer.server.v2.domain.reservation.repository.ReservationRepository;
import team.washer.server.v2.domain.reservation.service.ConfirmReservationService;
import team.washer.server.v2.global.common.error.exception.ExpectedException;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConfirmReservationServiceImpl implements ConfirmReservationService {

    private final ReservationRepository reservationRepository;

    @Override
    @Transactional
    public ReservationResDto execute(final Long userId, final Long reservationId) {
        final Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new ExpectedException("예약을 찾을 수 없습니다", HttpStatus.NOT_FOUND));

        if (!reservation.getUser().getId().equals(userId)) {
            throw new ExpectedException("예약을 확인할 권한이 없습니다", HttpStatus.FORBIDDEN);
        }

        if (!reservation.isReserved()) {
            throw new ExpectedException("예약 상태에서만 확인할 수 있습니다", HttpStatus.BAD_REQUEST);
        }

        if (reservation.isExpired()) {
            throw new ExpectedException("예약이 만료되었습니다", HttpStatus.BAD_REQUEST);
        }

        reservation.confirm();
        final Reservation saved = reservationRepository.save(reservation);
        log.info("Confirmed reservation {} by user {}", reservationId, userId);

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
