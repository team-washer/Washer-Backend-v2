package team.washer.server.v2.domain.reservation.service.impl;

import java.time.LocalDateTime;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import team.washer.server.v2.domain.reservation.dto.response.CancellationResDto;
import team.washer.server.v2.domain.reservation.entity.Reservation;
import team.washer.server.v2.domain.reservation.repository.ReservationRepository;
import team.washer.server.v2.domain.reservation.service.ApplyReservationPenaltyService;
import team.washer.server.v2.domain.reservation.service.CancelReservationService;
import team.washer.server.v2.domain.reservation.service.QueryPenaltyStatusService;
import team.washer.server.v2.domain.user.entity.User;
import team.washer.server.v2.global.common.error.exception.ExpectedException;

@Slf4j
@Service
@RequiredArgsConstructor
public class CancelReservationServiceImpl implements CancelReservationService {

    private final ReservationRepository reservationRepository;
    private final ApplyReservationPenaltyService applyReservationPenaltyService;
    private final QueryPenaltyStatusService queryPenaltyStatusService;

    @Override
    @Transactional
    public CancellationResDto execute(final Long userId, final Long reservationId) {
        final Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new ExpectedException("예약을 찾을 수 없습니다", HttpStatus.NOT_FOUND));

        if (!reservation.getUser().getId().equals(userId)) {
            throw new ExpectedException("예약을 취소할 권한이 없습니다", HttpStatus.FORBIDDEN);
        }

        if (!reservation.isActive()) {
            throw new ExpectedException("활성 예약만 취소할 수 있습니다", HttpStatus.BAD_REQUEST);
        }

        boolean applyPenalty = false;
        LocalDateTime penaltyExpiresAt = null;

        if (reservation.isReserved() && !reservation.isExpired()) {
            final User user = reservation.getUser();
            applyReservationPenaltyService.execute(user);
            penaltyExpiresAt = queryPenaltyStatusService.execute(userId).penaltyExpiresAt();
            applyPenalty = true;
            log.info("Applied penalty to user {} for cancelling reservation {}", userId, reservationId);
        }

        reservation.cancel();
        reservationRepository.save(reservation);
        log.info("Cancelled reservation {} by user {}", reservationId, userId);

        return mapToCancellationResDto(applyPenalty, penaltyExpiresAt);
    }

    private CancellationResDto mapToCancellationResDto(final boolean penaltyApplied,
            final LocalDateTime penaltyExpiresAt) {
        final String message = penaltyApplied ? "예약이 취소되었습니다. 10분간 예약이 제한됩니다." : "예약이 취소되었습니다.";
        return new CancellationResDto(true, message, penaltyApplied, penaltyExpiresAt);
    }
}
