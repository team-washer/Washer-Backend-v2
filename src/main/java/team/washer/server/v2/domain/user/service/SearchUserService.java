package team.washer.server.v2.domain.user.service;

import team.washer.server.v2.domain.user.dto.response.UserListResDto;

public interface SearchUserService {
    UserListResDto getUsersByFilter(String name, String roomNumber, Integer grade, Integer floor);
}
