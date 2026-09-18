package team.washer.server.v2.global.common.constants;

public final class ReservationConstants {
    private ReservationConstants() {
    }

    public static final int DEFAULT_RESERVATION_DURATION_MINUTES = 90;
    public static final int PAUSE_TIMEOUT_MINUTES = 10;

    /**
     * 예약 만료 후에도 기기 시작 여부를 확정할 수 없을 때 추가로 보류하는 시간(분). SmartThings capability 누락을 곧바로
     * 미사용으로 단정하지 않되, RESERVED 상태가 영구히 남지 않도록 절대 상한을 둔다.
     */
    public static final int UNKNOWN_START_DECISION_GRACE_MINUTES = 10;

    /**
     * 비정상 중단을 확정하기 전까지 연속으로 중단이 감지되어야 하는 폴링 횟수. 사이클 단계 전환 중 순간적으로 보고되는 정지를 진짜 중단으로
     * 오판하지 않도록 디바운스하는 데 사용한다. 라이프사이클 폴링 주기(30초) 기준 약 90초 연속 정지 시 확정된다.
     */
    public static final int INTERRUPTION_CONFIRM_THRESHOLD = 3;

    /**
     * 하나의 세탁·건조 사이클이 가질 수 있는 최대 길이(분). 기기가 보고한 완료 예정 시각이 이 범위를 벗어나면 이상치로 보고 저장하지
     * 않는다. 완료 후 세탁기 배수 유예({@link #WASHER_DRAIN_GRACE_MINUTES})의 기준 시각이 오염되는 것을 막기
     * 위한 상한이다.
     */
    public static final int MAX_REASONABLE_CYCLE_MINUTES = 240;

    /**
     * 예약 완료 후에도 작동 중인 세탁기의 전원 차단을 미루는 시간(분). 세탁기는 배수·탈수가 끝나기 전에 완료를 보고할 수 있으므로, 마지막
     * 완료 예약의 완료 예정 시각(없으면 실제 완료 시각)에 이 시간을 더한 시각까지는 작동 중인 세탁기의 전원을 끄지 않는다. 기기가 먼저
     * 정지하면 유예와 무관하게 전원을 차단한다.
     */
    public static final int WASHER_DRAIN_GRACE_MINUTES = 5;
}
