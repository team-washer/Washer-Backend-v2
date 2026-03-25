package team.washer.server.v2.domain.user.repository.custom.impl;

import static team.washer.server.v2.domain.user.entity.QUser.*;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;

import lombok.RequiredArgsConstructor;
import team.washer.server.v2.domain.user.entity.User;
import team.washer.server.v2.domain.user.repository.custom.UserRepositoryCustom;

@Repository
@RequiredArgsConstructor
public class UserRepositoryCustomImpl implements UserRepositoryCustom {

    private final JPAQueryFactory jpaQueryFactory;

    @Override
    public Page<User> findUsersByFilter(String name,
            String studentId,
            String roomNumber,
            Integer grade,
            Integer floor,
            Pageable pageable) {
        final List<User> content = jpaQueryFactory.selectFrom(user)
                .where(nameContains(name),
                        studentIdContains(studentId),
                        roomNumberEquals(roomNumber),
                        gradeEquals(grade),
                        floorEquals(floor))
                .orderBy(user.createdAt.desc()).offset(pageable.getOffset()).limit(pageable.getPageSize()).fetch();

        final Long total = jpaQueryFactory.select(user.count()).from(user)
                .where(nameContains(name),
                        studentIdContains(studentId),
                        roomNumberEquals(roomNumber),
                        gradeEquals(grade),
                        floorEquals(floor))
                .fetchOne();

        return new PageImpl<>(content, pageable, total != null ? total : 0L);
    }

    private BooleanExpression nameContains(String name) {
        return StringUtils.hasText(name) ? user.name.contains(name) : null;
    }

    private BooleanExpression studentIdContains(String studentId) {
        return StringUtils.hasText(studentId) ? user.studentId.contains(studentId) : null;
    }

    private BooleanExpression roomNumberEquals(String roomNumber) {
        return StringUtils.hasText(roomNumber) ? user.roomNumber.eq(roomNumber) : null;
    }

    private BooleanExpression gradeEquals(Integer grade) {
        return grade != null ? user.grade.eq(grade) : null;
    }

    private BooleanExpression floorEquals(Integer floor) {
        return floor != null ? user.floor.eq(floor) : null;
    }
}
