package team.washer.server.v2.domain.user.service.impl;

import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import team.themoment.sdk.exception.ExpectedException;
import team.washer.server.v2.domain.auth.repository.redis.RefreshTokenRedisRepository;
import team.washer.server.v2.domain.auth.util.WithdrawnStudentRedisUtil;
import team.washer.server.v2.domain.reservation.entity.Reservation;
import team.washer.server.v2.domain.reservation.support.UserReservationCleanupSupport;
import team.washer.server.v2.domain.user.repository.UserRepository;
import team.washer.server.v2.domain.user.service.WithdrawUserService;
import team.washer.server.v2.global.security.provider.CurrentUserProvider;

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

        try {
            refreshTokenRedisRepository.deleteById(userId);
        } catch (Exception e) {
            throw new RedisConnectionFailureException("리프레시 토큰을 폐기할 수 없습니다.", e);
        }

        // Redis 기록이 모두 성공한 뒤 DB 삭제를 시도한다. DB가 롤백되어 기록이 남아도 로그인은 기존 사용자를 먼저 확인한다.
        withdrawnStudentRedisUtil.markWithdrawn(user.getStudentId());

        userRepository.delete(user);
    }
}
