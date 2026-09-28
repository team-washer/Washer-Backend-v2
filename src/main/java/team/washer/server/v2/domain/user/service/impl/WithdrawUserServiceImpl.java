package team.washer.server.v2.domain.user.service.impl;

import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import team.themoment.sdk.exception.ExpectedException;
import team.washer.server.v2.domain.auth.repository.redis.RefreshTokenRedisRepository;
import team.washer.server.v2.domain.auth.util.WithdrawnStudentRedisUtil;
import team.washer.server.v2.domain.reservation.entity.Reservation;
import team.washer.server.v2.domain.reservation.support.UserReservationCleanupSupport;
import team.washer.server.v2.domain.user.repository.UserRepository;
import team.washer.server.v2.domain.user.service.WithdrawUserService;
import team.washer.server.v2.global.security.provider.CurrentUserProvider;

@Slf4j
@Service
@RequiredArgsConstructor
public class WithdrawUserServiceImpl implements WithdrawUserService {

    private final UserRepository userRepository;
    private final RefreshTokenRedisRepository refreshTokenRedisRepository;
    private final WithdrawnStudentRedisUtil withdrawnStudentRedisUtil;
    private final CurrentUserProvider currentUserProvider;
    private final UserReservationCleanupSupport userReservationCleanupSupport;

    @Override
    @Transactional
    public void execute() {
        final var userId = currentUserProvider.getCurrentUserId();
        final var user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new ExpectedException("사용자를 찾을 수 없습니다", HttpStatus.NOT_FOUND));

        final var activeReservations = userReservationCleanupSupport.lockActiveReservations(user);
        if (activeReservations.stream().anyMatch(Reservation::isRunning)) {
            throw new ExpectedException("기기 사용 중에는 회원탈퇴를 할 수 없습니다. 사용 완료 후 다시 시도해주세요.", HttpStatus.CONFLICT);
        }

        userReservationCleanupSupport.cancelAndReleaseMachines(activeReservations);

        // DB 삭제를 먼저 flush하여 DB 롤백이 Redis 기록보다 앞서 실패하도록 한다.
        userRepository.delete(user);
        userRepository.flush();

        final AtomicBoolean withdrawnRecordMayExist = new AtomicBoolean();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(final int status) {
                    if (status == STATUS_ROLLED_BACK && withdrawnRecordMayExist.get()) {
                        try {
                            withdrawnStudentRedisUtil.removeWithdrawn(user.getStudentId());
                        } catch (Exception exception) {
                            log.error("withdrawn record rollback cleanup failed", exception);
                        }
                    }
                }
            });
        }

        refreshTokenRedisRepository.deleteById(userId);
        withdrawnRecordMayExist.set(true);
        withdrawnStudentRedisUtil.markWithdrawn(user.getStudentId());
    }
}
