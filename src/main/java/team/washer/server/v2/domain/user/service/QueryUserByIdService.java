package team.washer.server.v2.domain.user.service;

import team.washer.server.v2.domain.user.dto.UserResponseDto;

public interface QueryUserByIdService {
    UserResponseDto getUserById(Long id);
}
