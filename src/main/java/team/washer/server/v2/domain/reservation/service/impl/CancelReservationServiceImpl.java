package team.washer.server.v2.domain.reservation.service.impl;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

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

    @Override
    @Transactional
    public CancellationResDto execute(final Long reservationId) {
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
            user.updateLastCancellationTime();
        }

        reservation.cancel();
        machine.releaseIfHeld();
        reservationRepository.save(reservation);
        machineRepository.save(machine);
        registerPenaltyAfterCommit(applyPenalty, user, machine, userId, reservationId);
        log.info("Cancelled reservation reservationId={} userId={}", reservationId, userId);

        return mapToCancellationResDto(applyPenalty);
    }

    private void registerPenaltyAfterCommit(final boolean applyPenalty,
            final User user,
            final Machine machine,
            final Long userId,
            final Long reservationId) {
        if (!applyPenalty) {
            return;
        }
        final Runnable apply = () -> {
            try {
                applyPenalty(user, machine, userId, reservationId);
            } catch (Exception e) {
                log.error("manual cancel penalty after commit failed userId={} reservationId={}",
                        userId,
                        reservationId,
                        e);
            }
        };
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    apply.run();
                }
            });
            return;
        }
        apply.run();
    }

    private void applyPenalty(final User user, final Machine machine, final Long userId, final Long reservationId) {
        penaltyRedisUtil.applyCooldown(userId, machine.getType());
        penaltyRedisUtil.recordCancellation(userId);
        if (penaltyRedisUtil.getCancellationCount(userId) > PenaltyConstants.MAX_CANCELLATIONS_IN_48H) {
            final RestrictionStatus previousBlockStatus = penaltyRedisUtil.checkBlock(user.getRoomNumber());
            if (penaltyRedisUtil.applyBlock(user.getRoomNumber())) {
                if (previousBlockStatus != RestrictionStatus.RESTRICTED) {
                    reservationNotificationSupport.sendCancellationBlock(user, machine);
                }
                log.warn("48h block applied roomNumber={}", user.getRoomNumber());
            }
        }
        log.info("manual cancel penalty applied userId={} reservationId={}", userId, reservationId);
    }

    private CancellationResDto mapToCancellationResDto(final boolean penaltyApplied) {
        final String message = penaltyApplied ? "예약이 취소되었습니다. 5분간 동일 종류 기기 재예약이 제한됩니다." : "예약이 취소되었습니다.";
        return new CancellationResDto(true, message, penaltyApplied, null);
    }
}
