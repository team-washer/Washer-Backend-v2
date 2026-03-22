package team.washer.server.v2.domain.user.service;

import team.washer.server.v2.domain.user.dto.response.UserUpdateResDto;

public interface UpdateUserInfoService {
    UserUpdateResDto execute(Long userId, String roomNumber, Integer grade, Integer floor);
}
