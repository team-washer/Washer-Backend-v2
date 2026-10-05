package team.washer.server.v2.domain.reservation.service.impl;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import team.washer.server.v2.domain.machine.entity.Machine;
import team.washer.server.v2.domain.machine.repository.MachineRepository;
import team.washer.server.v2.domain.notification.support.ReservationNotificationSupport;
import team.washer.server.v2.domain.reservation.dto.response.CancellationResDto;
import team.washer.server.v2.domain.reservation.entity.Reservation;
import team.washer.server.v2.domain.reservation.enums.RestrictionStatus;
import team.washer.server.v2.domain.reservation.repository.ReservationRepository;
import team.washer.server.v2.domain.reservation.service.CancelReservationService;
import team.washer.server.v2.domain.reservation.util.PenaltyRedisUtil;
import team.washer.server.v2.domain.user.entity.User;
import team.washer.server.v2.domain.user.repository.UserRepository;
import team.washer.server.v2.global.common.constants.PenaltyConstants;
import team.washer.server.v2.global.common.error.code.ErrorCode;
import team.washer.server.v2.global.common.error.exception.ErrorCodeException;
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
    private final UserRepository userRepository;
    private final TransactionOperations transactionOperations;

    @Override
    public CancellationResDto execute(final Long reservationId) {
        final CancellationContext cancellationContext = transactionOperations
                .execute(status -> cancelReservation(reservationId));
        return mapToCancellationResDto(cancellationContext);
    }

    private CancellationContext cancelReservation(final Long reservationId) {
        final var userId = currentUserProvider.getCurrentUserId();
        final var reservationUserId = reservationRepository.findUserIdById(reservationId)
                .orElseThrow(() -> new ErrorCodeException(ErrorCode.RESERVATION_NOT_FOUND));

        if (!reservationUserId.equals(userId)) {
            throw new ErrorCodeException(ErrorCode.RESERVATION_ACCESS_DENIED, "예약을 취소할 권한이 없습니다");
        }

        final var machineId = reservationRepository.findMachineIdById(reservationId)
                .orElseThrow(() -> new ErrorCodeException(ErrorCode.RESERVATION_NOT_FOUND));
        userRepository.findRoomUserIdsByUserIdForUpdate(reservationUserId);
        final User user = userRepository.findByIdForUpdate(reservationUserId)
                .orElseThrow(() -> new ErrorCodeException(ErrorCode.USER_NOT_FOUND));
        final var machine = machineRepository.findByIdForUpdate(machineId)
                .orElseThrow(() -> new ErrorCodeException(ErrorCode.MACHINE_NOT_FOUND));
        final Reservation reservation = reservationRepository.findByIdForUpdateWithoutRelations(reservationId)
                .orElseThrow(() -> new ErrorCodeException(ErrorCode.RESERVATION_NOT_FOUND));

        if (reservation.isRunning()) {
            throw new ErrorCodeException(ErrorCode.RESERVATION_CANCELLATION_CONFLICT,
                    "이미 기기 사용이 시작되어 예약을 취소할 수 없습니다. 최신 상태를 확인해주세요.");
        }

        if (!reservation.isReserved()) {
            throw new ErrorCodeException(ErrorCode.RESERVATION_STATE_INVALID, "취소할 수 있는 상태의 예약이 아닙니다");
        }

        // 수동 취소 시 패널티 적용. 단, 관리자 대리 예약은 본인이 요청한 것이 아니므로 면제한다
        final boolean applyPenalty = !reservation.isProxyReservation();
        if (applyPenalty) {
            user.updateLastCancellationTime(machine.getType());
        }

        reservation.cancel();
        machine.releaseIfHeld();
        reservationRepository.save(reservation);
        machineRepository.save(machine);
        log.info("Cancelled reservation reservationId={} userId={}", reservationId, userId);

        return new CancellationContext(user, machine, reservationId, applyPenalty);
    }

    private void applyPenalty(final User user, final Machine machine, final Long reservationId) {
        final Long userId = user.getId();
        penaltyRedisUtil.applyCooldown(userId, machine.getType());
        penaltyRedisUtil.recordCancellation(userId);
        if (penaltyRedisUtil.getCancellationCount(userId) > PenaltyConstants.MAX_CANCELLATIONS_IN_48H) {
            final RestrictionStatus previousBlockStatus = penaltyRedisUtil.checkBlock(user.getRoomNumber());
            if (penaltyRedisUtil.applyBlock(user.getRoomNumber())) {
                // 기존 차단 여부를 조회하지 못했다면 알림 누락보다 중복 발송이 낫다고 보고 발송한다
                if (previousBlockStatus != RestrictionStatus.RESTRICTED) {
                    sendCancellationBlockNotification(user, machine, reservationId);
                }
                log.warn("48h block applied roomNumber={}", user.getRoomNumber());
            }
        }
        log.info("manual cancel penalty applied userId={} reservationId={}", userId, reservationId);
    }

    private void sendCancellationBlockNotification(final User user, final Machine machine, final Long reservationId) {
        try {
            reservationNotificationSupport.sendCancellationBlock(user, machine);
        } catch (Exception e) {
            log.error("manual cancel block notification failed userId={} reservationId={}",
                    user.getId(),
                    reservationId,
                    e);
        }
    }

    private CancellationResDto mapToCancellationResDto(final CancellationContext cancellationContext) {
        if (!cancellationContext.applyPenalty()) {
            return new CancellationResDto(true, "예약이 취소되었습니다.", false, null);
        }

        try {
            applyPenalty(cancellationContext.user(),
                    cancellationContext.machine(),
                    cancellationContext.reservationId());
        } catch (Exception e) {
            log.error("manual cancel penalty application failed userId={} reservationId={}",
                    cancellationContext.user().getId(),
                    cancellationContext.reservationId(),
                    e);
        }

        LocalDateTime penaltyExpiresAt = null;
        try {
            penaltyExpiresAt = penaltyRedisUtil.getPenaltyExpiryTime(cancellationContext.user().getId());
        } catch (Exception e) {
            log.error("manual cancel penalty expiry lookup failed userId={} reservationId={}",
                    cancellationContext.user().getId(),
                    cancellationContext.reservationId(),
                    e);
        }
        return new CancellationResDto(true, "예약이 취소되었습니다. 재예약 제한이 적용될 수 있습니다.", true, penaltyExpiresAt);
    }

    private record CancellationContext(User user, Machine machine, Long reservationId, boolean applyPenalty) {
    }
}
