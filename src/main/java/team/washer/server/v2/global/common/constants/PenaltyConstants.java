package team.washer.server.v2.global.common.constants;

public final class PenaltyConstants {
    private PenaltyConstants() {
    }

    public static final int PENALTY_DURATION_MINUTES = 10;

    /** 예약 취소 후 재예약 쿨다운 시간 (분) */
    public static final int COOLDOWN_DURATION_MINUTES = 5;

    /** 타임아웃 경고 유효 기간 (일) */
    public static final int WARNING_DURATION_DAYS = 7;

    /** 48시간 내 최대 허용 취소 횟수. 초과 시 BLOCK 적용 */
    public static final int MAX_CANCELLATIONS_IN_48H = 4;

    /** 취소 횟수 카운팅 윈도우 및 BLOCK 지속 시간 (시간) */
    public static final int CANCELLATION_WINDOW_HOURS = 48;

    /** 취소 이력 sorted set Redis 키 프리픽스 */
    public static final String CANCEL_HISTORY_KEY_PREFIX = "reservation:cancel_history:";
}
