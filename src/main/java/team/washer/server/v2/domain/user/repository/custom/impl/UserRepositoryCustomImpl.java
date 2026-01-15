package team.washer.server.v2.domain.user.repository.custom.impl;

import static team.washer.server.v2.domain.user.entity.QUser.*;

import java.util.List;

import org.springframework.stereotype.Repository;

import com.querydsl.core.BooleanBuilder;
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
        BooleanBuilder builder = new BooleanBuilder();

        if (name != null && !name.isEmpty()) {
            builder.and(user.name.contains(name));
        }
        if (roomNumber != null && !roomNumber.isEmpty()) {
            builder.and(user.roomNumber.eq(roomNumber));
        }
        if (grade != null) {
            builder.and(user.grade.eq(grade));
        }
        if (floor != null) {
            builder.and(user.floor.eq(floor));
        }

        return queryFactory.selectFrom(user).where(builder).fetch();
    }
}
