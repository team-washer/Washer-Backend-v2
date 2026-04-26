package team.washer.server.v2.domain.notification.repository.custom;

import team.washer.server.v2.domain.user.entity.User;

public interface NotificationRepositoryCustom {

    /**
     * 사용자의 알림 중 최신 limit개를 제외한 오래된 알림을 삭제한다.
     *
     * @param user
     *            대상 사용자
     * @param limit
     *            유지할 최대 알림 개수
     * @return 삭제된 알림 수
     */
    int deleteOldestByUserExceedingLimit(User user, int limit);
}
