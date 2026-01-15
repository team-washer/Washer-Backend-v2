package team.washer.server.v2.domain.user.service.impl;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import team.washer.server.v2.domain.user.dto.UserListResponseDto;
import team.washer.server.v2.domain.user.dto.UserResponseDto;
import team.washer.server.v2.domain.user.entity.User;
import team.washer.server.v2.domain.user.repository.UserRepository;
import team.washer.server.v2.domain.user.service.SearchUserService;

@Service
@RequiredArgsConstructor
public class SearchUserServiceImpl implements SearchUserService {

    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public UserListResponseDto getUsersByFilter(String name, String roomNumber, Integer grade, Integer floor) {
        List<User> users = userRepository.findUsersByFilter(name, roomNumber, grade, floor);
        return buildUserListResponse(users);
    }

    private UserListResponseDto buildUserListResponse(List<User> users) {
        List<UserResponseDto> userDtos = users.stream()
                .map(user -> new UserResponseDto(user.getId(), user.getName(), user.getStudentId(),
                        user.getRoomNumber(), user.getGrade(), user.getFloor(), user.getPenaltyCount(),
                        user.getCreatedAt(), user.getUpdatedAt()))
                .collect(Collectors.toList());
        return new UserListResponseDto(userDtos, userDtos.size());
    }
}
