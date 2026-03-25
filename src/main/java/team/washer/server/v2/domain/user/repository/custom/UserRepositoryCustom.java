package team.washer.server.v2.domain.user.repository.custom;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import team.washer.server.v2.domain.user.entity.User;

public interface UserRepositoryCustom {

    Page<User> findUsersByFilter(String name,
            String studentId,
            String roomNumber,
            Integer grade,
            Integer floor,
            Pageable pageable);
}
