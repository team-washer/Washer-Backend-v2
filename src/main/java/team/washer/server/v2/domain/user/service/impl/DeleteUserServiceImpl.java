package team.washer.server.v2.domain.user.service.impl;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import team.themoment.sdk.exception.ExpectedException;
import team.washer.server.v2.domain.reservation.entity.Reservation;
import team.washer.server.v2.domain.reservation.support.UserReservationCleanupSupport;
import team.washer.server.v2.domain.user.repository.UserRepository;
import team.washer.server.v2.domain.user.service.DeleteUserService;

/**
 * 사용자 삭제 서비스 구현체
 */
@Service
@RequiredArgsConstructor
@Transactional
public class DeleteUserServiceImpl implements DeleteUserService {

    private final UserRepository userRepository;
    private final UserReservationCleanupSupport userReservationCleanupSupport;

    @Override
    public void execute(Long userId) {
        // 예약 생성과 직렬화하기 위해 사용자 행을 먼저 잠근다. 잠근 뒤에는 이 사용자의 새 예약이 생길 수 없다
        final var user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new ExpectedException("사용자를 찾을 수 없습니다", HttpStatus.NOT_FOUND));

        // 차단 판정과 정리를 같은 잠금 목록으로 수행한다. 만료 예약만 남은 사용자는 본인 탈퇴와 동일하게 삭제할 수 있어야 한다
        final var activeReservations = userReservationCleanupSupport.lockActiveReservations(user);
        if (activeReservations.stream().anyMatch(Reservation::isCurrentlyActive)) {
            throw new ExpectedException("활성 예약이 있는 사용자는 삭제할 수 없습니다", HttpStatus.BAD_REQUEST);
        }

        // 만료된 RESERVED 예약이 남아 기기를 붙잡고 있을 수 있다. cascade 삭제는 예약 행만 지우고
        // 기기의 availability는 되돌리지 않으므로, 본인 탈퇴와 동일하게 예약을 취소하고 기기를 해제한 뒤 삭제한다
        userReservationCleanupSupport.cancelAndReleaseMachines(activeReservations);

        // 물리적 삭제
        userRepository.delete(user);
    }
}
