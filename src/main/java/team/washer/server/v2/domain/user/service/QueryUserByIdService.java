package team.washer.server.v2.domain.user.service;

import team.washer.server.v2.domain.user.dto.response.UserResDto;

public interface QueryUserByIdService {
    UserResDto getUserById(Long id);
}
