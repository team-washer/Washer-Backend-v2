package team.washer.server.v2.global.common.constants;

public final class ReservationConstants {
    private ReservationConstants() {
    }

    public static final int DEFAULT_RESERVATION_DURATION_MINUTES = 90;
    public static final int PAUSE_TIMEOUT_MINUTES = 10;

    /**
     * 비정상 중단을 확정하기 전까지 연속으로 중단이 감지되어야 하는 폴링 횟수. 사이클 단계 전환 중 순간적으로 보고되는 정지를 진짜 중단으로
     * 오판하지 않도록 디바운스하는 데 사용한다. 라이프사이클 폴링 주기(30초) 기준 약 90초 연속 정지 시 확정된다.
     */
    public static final int INTERRUPTION_CONFIRM_THRESHOLD = 3;
}
