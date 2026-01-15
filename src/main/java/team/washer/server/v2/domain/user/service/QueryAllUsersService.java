package team.washer.server.v2.domain.user.service;

import team.washer.server.v2.domain.user.dto.UserListResponseDto;

public interface QueryAllUsersService {
    UserListResponseDto getAllUsers();
}
