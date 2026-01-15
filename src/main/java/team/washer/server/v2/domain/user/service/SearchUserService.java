package team.washer.server.v2.domain.user.service;

import team.washer.server.v2.domain.user.dto.UserListResponseDto;

public interface SearchUserService {
    UserListResponseDto getUsersByFilter(String name, String roomNumber, Integer grade, Integer floor);
}
