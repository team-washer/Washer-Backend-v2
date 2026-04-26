package team.washer.server.v2.domain.user.service.impl;

import java.time.Duration;
import java.time.LocalDateTime;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import team.themoment.sdk.exception.ExpectedException;
import team.washer.server.v2.domain.reservation.util.PenaltyRedisUtil;
import team.washer.server.v2.domain.user.dto.response.UserResDto;
import team.washer.server.v2.domain.user.entity.User;
import team.washer.server.v2.domain.user.repository.UserRepository;
import team.washer.server.v2.domain.user.service.QueryUserByIdService;

@Service
@RequiredArgsConstructor
public class QueryUserByIdServiceImpl implements QueryUserByIdService {

    private final UserRepository userRepository;
    private final PenaltyRedisUtil penaltyRedisUtil;

    @Override
    @Transactional(readOnly = true)
    public UserResDto getUserById(Long id) {
        final User user = userRepository.findById(id)
                .orElseThrow(() -> new ExpectedException("사용자를 찾을 수 없습니다", HttpStatus.NOT_FOUND));

        final LocalDateTime penaltyExpiresAt = penaltyRedisUtil.getPenaltyExpiryTime(user.getId());
        final boolean isPenalized = penaltyExpiresAt != null && LocalDateTime.now().isBefore(penaltyExpiresAt);

        final Long penaltyRemainMinutes = isPenalized
                ? Duration.between(LocalDateTime.now(), penaltyExpiresAt).toMinutes()
                : null;
        final String penaltyReason = isPenalized ? buildPenaltyReason(user.getId()) : null;

        return new UserResDto(user.getId(),
                user.getName(),
                user.getStudentId(),
                user.getRoomNumber(),
                user.getGrade(),
                user.getFloor(),
                user.getPenaltyCount(),
                penaltyRemainMinutes,
                penaltyReason,
                user.getCreatedAt(),
                user.getUpdatedAt());
    }

    private String buildPenaltyReason(Long userId) {
        final String storedReason = penaltyRedisUtil.getPenaltyReason(userId);
        if (storedReason != null) {
            return storedReason;
        }
        final long cancelCount = penaltyRedisUtil.getCancellationCount(userId);
        return "48시간 내 취소 " + cancelCount + "회 누적";
    }
}
