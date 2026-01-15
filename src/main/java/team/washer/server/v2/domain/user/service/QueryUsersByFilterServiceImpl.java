package team.washer.server.v2.domain.user.service;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import team.washer.server.v2.domain.user.dto.UserListResponseDto;
import team.washer.server.v2.domain.user.dto.UserResponseDto;
import team.washer.server.v2.domain.user.entity.User;
import team.washer.server.v2.domain.user.repository.UserRepository;
@Service
@RequiredArgsConstructor
public class QueryUsersByFilterServiceImpl implements QueryUsersByFilterService {
    private final UserRepository userRepository;
    @Override
    @Transactional(readOnly = true)
    public UserListResponseDto getUsersByFilter(String name, String roomNumber, Integer grade, Integer floor) {
        List<User> users = new ArrayList<>();
        if (name != null && !name.isEmpty()) {
            users = userRepository.findByNameContaining(name);
        } else if (roomNumber != null && !roomNumber.isEmpty()) {
            users = userRepository.findByRoomNumber(roomNumber);
        } else if (grade != null && floor != null) {
            users = userRepository.findByFloorAndGrade(floor, grade);
        } else if (grade != null) {
            users = userRepository.findByGrade(grade);
        } else if (floor != null) {
            users = userRepository.findByFloor(floor);
        } else {
            users = userRepository.findAll();
        }
        return buildUserListResponse(users);
    }
    private UserListResponseDto buildUserListResponse(List<User> users) {
        List<UserResponseDto> userDtos = users.stream().map(UserResponseDto::from).collect(Collectors.toList());
        return UserListResponseDto.builder().users(userDtos).totalCount(userDtos.size()).build();
    }
}
