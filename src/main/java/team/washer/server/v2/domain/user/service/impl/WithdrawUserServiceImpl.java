package team.washer.server.v2.domain.user.service.impl;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import team.themoment.sdk.exception.ExpectedException;
import team.washer.server.v2.domain.auth.entity.WithdrawnStudent;
import team.washer.server.v2.domain.auth.repository.WithdrawnStudentRepository;
import team.washer.server.v2.domain.auth.repository.redis.RefreshTokenRedisRepository;
import team.washer.server.v2.domain.auth.util.WithdrawnStudentRedisUtil;
import team.washer.server.v2.domain.reservation.entity.Reservation;
import team.washer.server.v2.domain.reservation.support.UserReservationCleanupSupport;
import team.washer.server.v2.domain.user.repository.UserRepository;
import team.washer.server.v2.domain.user.service.WithdrawUserService;
import team.washer.server.v2.global.security.provider.CurrentUserProvider;
import team.washer.server.v2.global.util.DateTimeUtil;

@Slf4j
@Service
@RequiredArgsConstructor
public class WithdrawUserServiceImpl implements WithdrawUserService {

    private final UserRepository userRepository;
    private final WithdrawnStudentRepository withdrawnStudentRepository;
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

        final var withdrawnExpiresAt = DateTimeUtil.nowInKorea().plusDays(30);
        withdrawnStudentRepository.findByStudentId(user.getStudentId()).ifPresentOrElse(
                withdrawnStudent -> withdrawnStudent.renew(withdrawnExpiresAt),
                () -> withdrawnStudentRepository.save(WithdrawnStudent.builder().studentId(user.getStudentId())
                        .expiresAt(withdrawnExpiresAt).build()));
        withdrawnStudentRepository.flush();

        // DB 탈퇴 기록과 사용자 삭제를 같은 트랜잭션에 포함하고 Redis 변경은 DB 커밋 이후에 수행한다.
        userRepository.delete(user);
        userRepository.flush();

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(final int status) {
                    if (status == STATUS_COMMITTED) {
                        applyRedisSideEffects(userId, user.getStudentId());
                    }
                }
            });
            return;
        }

        applyRedisSideEffects(userId, user.getStudentId());
    }

    private void applyRedisSideEffects(final Long userId, final String studentId) {
        try {
            withdrawnStudentRedisUtil.markWithdrawn(studentId);
        } catch (Exception exception) {
            log.error("withdrawn student record synchronization failed", exception);
        }

        try {
            refreshTokenRedisRepository.deleteById(userId);
        } catch (Exception exception) {
            log.error("refresh token synchronization failed", exception);
        }
    }
}
