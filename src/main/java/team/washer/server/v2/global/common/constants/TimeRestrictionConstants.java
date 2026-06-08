package team.washer.server.v2.global.common.constants;

import java.time.LocalTime;

public final class TimeRestrictionConstants {
    private TimeRestrictionConstants() {
    }

    /** 월~목 예약 가능 시작 시각 (전 학년 공통) */
    public static final LocalTime WEEKDAY_START_TIME = LocalTime.of(21, 20);

    /** 일요일 1학년 예약 가능 시작 시각 */
    public static final LocalTime SUNDAY_GRADE_1_START_TIME = LocalTime.of(20, 0);

    /** 일요일 2학년 예약 가능 시작 시각 */
    public static final LocalTime SUNDAY_GRADE_2_START_TIME = LocalTime.of(20, 20);

    /** 일요일 3학년 이상 예약 가능 시작 시각 */
    public static final LocalTime SUNDAY_GRADE_3_START_TIME = LocalTime.of(20, 40);
}
