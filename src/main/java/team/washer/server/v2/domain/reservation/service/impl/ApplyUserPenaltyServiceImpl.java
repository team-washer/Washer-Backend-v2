package team.washer.server.v2.domain.reservation.service.impl;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import team.themoment.sdk.exception.ExpectedException;
import team.washer.server.v2.domain.notification.support.ReservationNotificationSupport;
import team.washer.server.v2.domain.reservation.service.ApplyUserPenaltyService;
import team.washer.server.v2.domain.reservation.util.PenaltyRedisUtil;
import team.washer.server.v2.domain.user.entity.User;
import team.washer.server.v2.domain.user.repository.UserRepository;
import team.washer.server.v2.global.security.provider.CurrentUserProvider;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApplyUserPenaltyServiceImpl implements ApplyUserPenaltyService {

    private final UserRepository userRepository;
    private final PenaltyRedisUtil penaltyRedisUtil;
    private final ReservationNotificationSupport reservationNotificationSupport;
    private final CurrentUserProvider currentUserProvider;

    @Override
    @Transactional
    public void execute(final Long userId, final String reason) {
        final var actorId = currentUserProvider.getCurrentUserId();

        // 권한 검사는 SecurityConfig가 담당한다(DORMITORY_COUNCIL, ADMIN 허용).
        // 자치위·관리자 구분은 감사 로그에만 남기므로 actor는 조회만 한다.
        final User actor = userRepository.findById(actorId)
                .orElseThrow(() -> new ExpectedException("사용자를 찾을 수 없습니다.", HttpStatus.NOT_FOUND));

        if (actorId.equals(userId)) {
            throw new ExpectedException("자신에게는 패널티를 부과할 수 없습니다.", HttpStatus.BAD_REQUEST);
        }

        final User target = userRepository.findById(userId)
                .orElseThrow(() -> new ExpectedException("사용자를 찾을 수 없습니다.", HttpStatus.NOT_FOUND));

        final String roomNumber = target.getRoomNumber();
        if (roomNumber == null) {
            throw new ExpectedException("호실 정보를 찾을 수 없습니다.", HttpStatus.NOT_FOUND);
        }

        // applyBlock은 예외를 삼키므로 부과 성공 여부를 직접 검증한다.
        // 관리자에게 거짓 성공 응답이 나가면 제재가 집행되지 않은 채 종료된다.
        penaltyRedisUtil.applyBlock(roomNumber);
        if (!penaltyRedisUtil.isBlocked(roomNumber)) {
            log.error("failed to apply admin penalty roomNumber={} targetId={} actorId={}",
                    roomNumber,
                    userId,
                    actorId);
            throw new ExpectedException("패널티 부과에 실패했습니다. 잠시 후 다시 시도해 주세요.", HttpStatus.INTERNAL_SERVER_ERROR);
        }

        log.info("admin penalty applied roomNumber={} targetId={} actorId={} actorRole={} reason={}",
                roomNumber,
                userId,
                actorId,
                actor.getRole(),
                reason);

        reservationNotificationSupport.sendAdminPenalty(target, reason);
    }
}
