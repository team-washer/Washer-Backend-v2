package team.washer.server.v2.domain.reservation.service.impl;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import team.themoment.sdk.exception.ExpectedException;
import team.washer.server.v2.domain.reservation.repository.ReservationRepository;
import team.washer.server.v2.domain.reservation.service.ConfirmReservationService;
import team.washer.server.v2.global.security.provider.CurrentUserProvider;

@Service
@RequiredArgsConstructor
@Slf4j
public class ConfirmReservationServiceImpl implements ConfirmReservationService {

    private final ReservationRepository reservationRepository;
    private final CurrentUserProvider currentUserProvider;

    @Override
    @Transactional
    public void execute(Long reservationId) {
        final var userId = currentUserProvider.getCurrentUserId();
        var reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new ExpectedException("예약을 찾을 수 없습니다", HttpStatus.NOT_FOUND));

        if (!reservation.getUser().getId().equals(userId)) {
            throw new ExpectedException("본인의 예약만 확인할 수 있습니다", HttpStatus.FORBIDDEN);
        }

        reservation.confirm();
        reservationRepository.save(reservation);

        log.info("Reservation confirmed: id={}, userId={}", reservationId, userId);
    }
}
