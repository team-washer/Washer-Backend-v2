package team.washer.server.v2.global.security.jwt.dto;

import team.washer.server.v2.domain.user.enums.UserRole;

public record JwtPayload(Long userId, UserRole role) {
}
