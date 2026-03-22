package team.washer.server.v2.domain.auth.service;

import team.washer.server.v2.domain.auth.dto.response.TokenResDto;
import team.washer.server.v2.domain.user.enums.UserRole;

public interface GenerateTokenService {
    TokenResDto execute(Long userId, UserRole role);
}
