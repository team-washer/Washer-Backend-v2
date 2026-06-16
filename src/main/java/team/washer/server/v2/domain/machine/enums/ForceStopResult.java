package team.washer.server.v2.domain.machine.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ForceStopResult {
    STOPPED("정지 명령 전송 완료"), ALREADY_STOPPED("이미 정지 상태"), SKIPPED_UNKNOWN("상태 확인 불가");

    private final String description;
}
