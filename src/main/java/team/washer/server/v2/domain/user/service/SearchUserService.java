package team.washer.server.v2.domain.user.service;

import org.springframework.data.domain.Pageable;

import team.washer.server.v2.domain.user.dto.response.UserListResDto;

public interface SearchUserService {
    UserListResDto execute(String name,
            String studentId,
            String roomNumber,
            Integer grade,
            Integer floor,
            Pageable pageable);
}
