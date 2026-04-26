package team.washer.server.v2.domain.reservation.service.impl;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import team.themoment.sdk.exception.ExpectedException;
import team.washer.server.v2.domain.machine.repository.MachineRepository;
import team.washer.server.v2.domain.reservation.dto.response.CancellationResDto;
import team.washer.server.v2.domain.reservation.entity.Reservation;
import team.washer.server.v2.domain.reservation.repository.ReservationRepository;
import team.washer.server.v2.domain.reservation.service.CancelReservationService;
import team.washer.server.v2.domain.reservation.util.PenaltyRedisUtil;
import team.washer.server.v2.domain.user.entity.User;
import team.washer.server.v2.global.common.constants.PenaltyConstants;
import team.washer.server.v2.global.security.provider.CurrentUserProvider;

@Slf4j
@Service
@RequiredArgsConstructor
public class CancelReservationServiceImpl implements CancelReservationService {

    private final ReservationRepository reservationRepository;
    private final MachineRepository machineRepository;
    private final PenaltyRedisUtil penaltyRedisUtil;
    private final CurrentUserProvider currentUserProvider;

    @Override
    @Transactional
    public CancellationResDto execute(final Long reservationId) {
        final var userId = currentUserProvider.getCurrentUserId();
        final Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new ExpectedException("예약을 찾을 수 없습니다", HttpStatus.NOT_FOUND));

        if (!reservation.getUser().getId().equals(userId)) {
            throw new ExpectedException("예약을 취소할 권한이 없습니다", HttpStatus.FORBIDDEN);
        }

        if (!reservation.isActive()) {
            throw new ExpectedException("활성 예약만 취소할 수 있습니다", HttpStatus.BAD_REQUEST);
        }

        boolean applyPenalty = false;

        // RESERVED 상태에서 수동 취소 시 패널티 적용
        if (reservation.isReserved()) {
            final User user = reservation.getUser();
            penaltyRedisUtil.applyCooldown(userId);
            penaltyRedisUtil.recordCancellation(userId);
            user.updateLastCancellationTime();
            if (penaltyRedisUtil.getCancellationCount(userId) > PenaltyConstants.MAX_CANCELLATIONS_IN_48H) {
                penaltyRedisUtil.applyBlock(user.getRoomNumber());
                log.warn("48h block applied roomNumber {}", user.getRoomNumber());
            }
            applyPenalty = true;
            log.info("manual cancel penalty applied userId {} reservationId {}", userId, reservationId);
        }

        final var machine = reservation.getMachine();
        reservation.cancel();
        machine.markAsAvailable();
        reservationRepository.save(reservation);
        machineRepository.save(machine);
        log.info("Cancelled reservation {} by user {}", reservationId, userId);

        return mapToCancellationResDto(applyPenalty);
    }

    private CancellationResDto mapToCancellationResDto(final boolean penaltyApplied) {
        final String message = penaltyApplied ? "예약이 취소되었습니다. 5분간 재예약이 제한됩니다." : "예약이 취소되었습니다.";
        return new CancellationResDto(true, message, penaltyApplied, null);
    }
}
