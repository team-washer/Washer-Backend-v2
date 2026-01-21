package team.washer.server.v2.domain.reservation.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ReservationStatus {
    RESERVED("예약됨", 5), CONFIRMED("확인됨", 2), RUNNING("실행 중", 0), COMPLETED("완료", 0), CANCELLED("취소", 0);

    private final String description;
    private final int timeoutMinutes;

    public boolean hasTimeout() {
        return timeoutMinutes > 0;
    }
}
