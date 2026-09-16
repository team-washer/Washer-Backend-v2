package team.washer.server.v2.domain.reservation.service.impl;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import team.themoment.sdk.exception.ExpectedException;
import team.washer.server.v2.domain.machine.repository.MachineRepository;
import team.washer.server.v2.domain.notification.support.ReservationNotificationSupport;
import team.washer.server.v2.domain.reservation.dto.response.CancellationResDto;
import team.washer.server.v2.domain.reservation.entity.Reservation;
import team.washer.server.v2.domain.reservation.enums.RestrictionStatus;
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
    private final ReservationNotificationSupport reservationNotificationSupport;
    private final CurrentUserProvider currentUserProvider;

    @Override
    @Transactional
    public CancellationResDto execute(final Long reservationId) {
        final var userId = currentUserProvider.getCurrentUserId();
        final Reservation reservation = reservationRepository.findByIdForUpdate(reservationId)
                .orElseThrow(() -> new ExpectedException("예약을 찾을 수 없습니다", HttpStatus.NOT_FOUND));

        if (!reservation.getUser().getId().equals(userId)) {
            throw new ExpectedException("예약을 취소할 권한이 없습니다", HttpStatus.FORBIDDEN);
        }

        if (reservation.isRunning()) {
            throw new ExpectedException("이미 기기 사용이 시작되어 예약을 취소할 수 없습니다. 최신 상태를 확인해주세요.", HttpStatus.CONFLICT);
        }

        if (!reservation.isReserved()) {
            throw new ExpectedException("취소할 수 있는 상태의 예약이 아닙니다", HttpStatus.BAD_REQUEST);
        }

        // 수동 취소 시 패널티 적용. 단, 관리자 대리 예약은 본인이 요청한 것이 아니므로 면제한다
        final boolean applyPenalty = !reservation.isProxyReservation();
        if (applyPenalty) {
            final User user = reservation.getUser();
            penaltyRedisUtil.applyCooldown(userId, reservation.getMachine().getType());
            penaltyRedisUtil.recordCancellation(userId);
            user.updateLastCancellationTime();
            if (penaltyRedisUtil.getCancellationCount(userId) > PenaltyConstants.MAX_CANCELLATIONS_IN_48H) {
                final RestrictionStatus previousBlockStatus = penaltyRedisUtil.checkBlock(user.getRoomNumber());
                // 차단 저장 실패는 PenaltyRedisUtil이 운영 알림으로 보고하며, 예약 취소 자체는 계속 진행한다
                if (penaltyRedisUtil.applyBlock(user.getRoomNumber())) {
                    // 기존 차단 여부를 조회하지 못했다면 알림 누락보다 중복 발송이 낫다고 보고 발송한다
                    if (previousBlockStatus != RestrictionStatus.RESTRICTED) {
                        reservationNotificationSupport.sendCancellationBlock(user, reservation.getMachine());
                    }
                    log.warn("48h block applied roomNumber={}", user.getRoomNumber());
                }
            }
            log.info("manual cancel penalty applied userId={} reservationId={}", userId, reservationId);
        }

        final var machine = reservation.getMachine();
        reservation.cancel();
        machine.releaseIfHeld();
        reservationRepository.save(reservation);
        machineRepository.save(machine);
        log.info("Cancelled reservation reservationId={} userId={}", reservationId, userId);

        return mapToCancellationResDto(applyPenalty);
    }

    private CancellationResDto mapToCancellationResDto(final boolean penaltyApplied) {
        final String message = penaltyApplied ? "예약이 취소되었습니다. 5분간 동일 종류 기기 재예약이 제한됩니다." : "예약이 취소되었습니다.";
        return new CancellationResDto(true, message, penaltyApplied, null);
    }
}
