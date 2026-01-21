package team.washer.server.v2.domain.reservation.service.impl;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import team.washer.server.v2.domain.reservation.service.QuerySundayReservationActiveService;
import team.washer.server.v2.domain.reservation.service.ValidateTimeRestrictionService;
import team.washer.server.v2.domain.user.entity.User;

/**
 * 예약 시간 제한 검증 서비스 구현체
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ValidateTimeRestrictionServiceImpl implements ValidateTimeRestrictionService {

    private static final LocalTime WEEKDAY_RESTRICTION_TIME = LocalTime.of(21, 10);

    private final QuerySundayReservationActiveService querySundayReservationActiveService;

    @Override
    @Transactional(readOnly = true)
    public void execute(final User user, final LocalDateTime startTime) {
        if (user.canBypassTimeRestrictions()) {
            return;
        }

        final DayOfWeek dayOfWeek = startTime.getDayOfWeek();
        final LocalTime time = startTime.toLocalTime();

        switch (dayOfWeek) {
            case MONDAY, TUESDAY, WEDNESDAY, THURSDAY :
                if (time.isBefore(WEEKDAY_RESTRICTION_TIME)) {
                    throw new IllegalArgumentException(
                            String.format("월요일부터 목요일까지는 %s 이후에만 예약할 수 있습니다", WEEKDAY_RESTRICTION_TIME));
                }
                break;

            case FRIDAY, SATURDAY :
                break;

            case SUNDAY :
                if (!querySundayReservationActiveService.execute()) {
                    throw new IllegalArgumentException("일요일 예약은 현재 비활성화되어 있습니다");
                }
                break;
        }

        log.debug("Time restriction validation passed for user {} at {}", user.getId(), startTime);
    }
}
