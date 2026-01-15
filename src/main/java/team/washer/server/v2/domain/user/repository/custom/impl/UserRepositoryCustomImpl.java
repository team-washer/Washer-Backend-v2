package team.washer.server.v2.domain.user.repository.custom.impl;

import static team.washer.server.v2.domain.user.entity.QUser.*;

import java.util.List;

import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import com.querydsl.jpa.impl.JPAQueryFactory;

import lombok.RequiredArgsConstructor;
import team.washer.server.v2.domain.user.entity.User;
import team.washer.server.v2.domain.user.repository.custom.UserRepositoryCustom;

@Repository
@RequiredArgsConstructor
public class UserRepositoryCustomImpl implements UserRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public List<User> findUsersByFilter(String name, String roomNumber, Integer grade, Integer floor) {
        return queryFactory.selectFrom(user)
                .where(StringUtils.hasText(name) ? user.name.contains(name) : null,
                        StringUtils.hasText(roomNumber) ? user.roomNumber.eq(roomNumber) : null,
                        grade != null ? user.grade.eq(grade) : null, floor != null ? user.floor.eq(floor) : null)
                .fetch();
    }
}
