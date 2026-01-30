package team.washer.server.v2.domain.reservation.service.impl;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import team.washer.server.v2.domain.reservation.dto.response.AdminCancellationResDto;
import team.washer.server.v2.domain.reservation.enums.ReservationStatus;
import team.washer.server.v2.domain.reservation.repository.ReservationRepository;
import team.washer.server.v2.domain.reservation.service.AdminCancelReservationService;
import team.washer.server.v2.global.common.error.exception.ExpectedException;

@Service
@RequiredArgsConstructor
public class AdminCancelReservationServiceImpl implements AdminCancelReservationService {

    private final ReservationRepository reservationRepository;

    @Transactional
    @Override
    public AdminCancellationResDto execute(Long reservationId) {
        final var reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new ExpectedException("예약을 찾을 수 없습니다", HttpStatus.NOT_FOUND));
        if (reservation.getStatus() == ReservationStatus.COMPLETED) {
            throw new ExpectedException("이미 완료된 예약은 취소할 수 없습니다", HttpStatus.BAD_REQUEST);
        }
        if (reservation.getStatus() == ReservationStatus.CANCELLED) {
            throw new ExpectedException("이미 취소된 예약입니다", HttpStatus.BAD_REQUEST);
        }
        reservation.cancel();
        final var machine = reservation.getMachine();
        machine.markAsAvailable();
        final var savedReservation = reservationRepository.save(reservation);
        return new AdminCancellationResDto(savedReservation.getId(),
                savedReservation.getUser().getName(),
                savedReservation.getMachine().getName(),
                savedReservation.getCancelledAt(),
                false);
    }
}
