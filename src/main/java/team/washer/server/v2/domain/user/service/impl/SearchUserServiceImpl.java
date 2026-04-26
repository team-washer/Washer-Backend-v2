package team.washer.server.v2.domain.user.service.impl;

import java.time.Duration;
import java.time.LocalDateTime;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import team.washer.server.v2.domain.reservation.util.PenaltyRedisUtil;
import team.washer.server.v2.domain.user.dto.response.UserListResDto;
import team.washer.server.v2.domain.user.dto.response.UserResDto;
import team.washer.server.v2.domain.user.entity.User;
import team.washer.server.v2.domain.user.repository.UserRepository;
import team.washer.server.v2.domain.user.service.SearchUserService;

@Service
@RequiredArgsConstructor
public class SearchUserServiceImpl implements SearchUserService {

    private final UserRepository userRepository;
    private final PenaltyRedisUtil penaltyRedisUtil;

    @Override
    @Transactional(readOnly = true)
    public UserListResDto execute(String name,
            String studentId,
            String roomNumber,
            Integer grade,
            Integer floor,
            Pageable pageable) {
        final Page<User> usersPage = userRepository
                .findUsersByFilter(name, studentId, roomNumber, grade, floor, pageable);
        final var userDtos = usersPage.getContent().stream().map(this::toUserResDto).toList();
        return new UserListResDto(userDtos,
                usersPage.getTotalElements(),
                usersPage.getTotalPages(),
                usersPage.getNumber());
    }

    private String buildPenaltyReason(Long userId) {
        final long cancelCount = penaltyRedisUtil.getCancellationCount(userId);
        final String storedReason = penaltyRedisUtil.getPenaltyReason(userId);
        if (storedReason != null) {
            return storedReason;
        }
        return "48시간 내 취소 " + cancelCount + "회 누적";
    }

    private UserResDto toUserResDto(User user) {
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
}
