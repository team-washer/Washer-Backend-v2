package team.washer.server.v2.domain.user.service.impl;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import team.washer.server.v2.domain.user.dto.response.UserListResDto;
import team.washer.server.v2.domain.user.dto.response.UserResDto;
import team.washer.server.v2.domain.user.entity.User;
import team.washer.server.v2.domain.user.repository.UserRepository;
import team.washer.server.v2.domain.user.service.SearchUserService;

@Service
@RequiredArgsConstructor
public class SearchUserServiceImpl implements SearchUserService {

    private final UserRepository userRepository;

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

    private UserResDto toUserResDto(User user) {
        return new UserResDto(user.getId(),
                user.getName(),
                user.getStudentId(),
                user.getRoomNumber(),
                user.getGrade(),
                user.getFloor(),
                user.getPenaltyCount(),
                user.getCreatedAt(),
                user.getUpdatedAt());
    }
}
