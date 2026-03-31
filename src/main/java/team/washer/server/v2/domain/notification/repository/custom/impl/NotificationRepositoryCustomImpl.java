package team.washer.server.v2.domain.notification.repository.custom.impl;

import static team.washer.server.v2.domain.notification.entity.QNotification.*;

import java.util.List;

import org.springframework.stereotype.Repository;

import com.querydsl.jpa.impl.JPAQueryFactory;

import lombok.RequiredArgsConstructor;
import team.washer.server.v2.domain.notification.repository.custom.NotificationRepositoryCustom;
import team.washer.server.v2.domain.user.entity.User;

@Repository
@RequiredArgsConstructor
public class NotificationRepositoryCustomImpl implements NotificationRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public int deleteOldestByUserExceedingLimit(final User user, final int limit) {
        final List<Long> keepIds = queryFactory.select(notification.id).from(notification)
                .where(notification.user.eq(user)).orderBy(notification.createdAt.desc()).limit(limit).fetch();

        if (keepIds.isEmpty()) {
            return 0;
        }

        return (int) queryFactory.delete(notification)
                .where(notification.user.eq(user).and(notification.id.notIn(keepIds))).execute();
    }
}
