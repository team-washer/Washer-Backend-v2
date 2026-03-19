package team.washer.server.v2.domain.user.service.impl;

import java.time.LocalDateTime;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import team.themoment.sdk.exception.ExpectedException;
import team.washer.server.v2.domain.reservation.util.PenaltyRedisUtil;
import team.washer.server.v2.domain.user.dto.response.MyInfoResDto;
import team.washer.server.v2.domain.user.repository.UserRepository;
import team.washer.server.v2.domain.user.service.QueryMyInfoService;
import team.washer.server.v2.global.security.provider.CurrentUserProvider;

@Service
@RequiredArgsConstructor
public class QueryMyInfoServiceImpl implements QueryMyInfoService {

    private final UserRepository userRepository;
    private final PenaltyRedisUtil penaltyRedisUtil;
    private final CurrentUserProvider currentUserProvider;

    @Override
    @Transactional(readOnly = true)
    public MyInfoResDto execute() {
        final var userId = currentUserProvider.getCurrentUserId();
        final var user = userRepository.findById(userId)
                .orElseThrow(() -> new ExpectedException("사용자를 찾을 수 없습니다", HttpStatus.NOT_FOUND));

        final LocalDateTime penaltyExpiresAt = penaltyRedisUtil.getPenaltyExpiryTime(userId);
        final boolean canReserve = penaltyExpiresAt == null || LocalDateTime.now().isAfter(penaltyExpiresAt);

        return new MyInfoResDto(user.getId(),
                user.getName(),
                user.getStudentId(),
                user.getRoomNumber(),
                user.getGrade(),
                user.getFloor(),
                user.getPenaltyCount(),
                user.getCreatedAt(),
                user.getUpdatedAt(),
                canReserve,
                canReserve ? null : penaltyExpiresAt);
    }
}
