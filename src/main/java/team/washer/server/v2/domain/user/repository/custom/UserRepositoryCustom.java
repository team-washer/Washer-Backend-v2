package team.washer.server.v2.domain.user.repository.custom;

import java.util.List;

import team.washer.server.v2.domain.user.entity.User;

public interface UserRepositoryCustom {

    List<User> findUsersByFilter(String name, String roomNumber, Integer grade, Integer floor);
}
