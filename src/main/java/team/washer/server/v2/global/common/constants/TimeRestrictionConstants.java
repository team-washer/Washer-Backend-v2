package team.washer.server.v2.global.common.constants;

import java.time.LocalTime;

public final class TimeRestrictionConstants {
    private TimeRestrictionConstants() {
    }

    /** 1학년 예약 가능 시작 시각 */
    public static final LocalTime GRADE_1_START_TIME = LocalTime.of(19, 50);

    /** 2학년 예약 가능 시작 시각 */
    public static final LocalTime GRADE_2_START_TIME = LocalTime.of(20, 10);

    /** 3학년 이상 예약 가능 시작 시각 */
    public static final LocalTime GRADE_3_START_TIME = LocalTime.of(20, 30);
}
