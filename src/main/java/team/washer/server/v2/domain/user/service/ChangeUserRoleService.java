package team.washer.server.v2.domain.user.service;

import team.washer.server.v2.domain.user.dto.response.UserRoleUpdateResDto;
import team.washer.server.v2.domain.user.enums.UserRole;

public interface ChangeUserRoleService {
    UserRoleUpdateResDto execute(Long targetUserId, UserRole newRole);
}
