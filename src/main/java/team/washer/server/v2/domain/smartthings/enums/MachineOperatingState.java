package team.washer.server.v2.domain.smartthings.enums;

import java.util.Arrays;

import com.fasterxml.jackson.annotation.JsonValue;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * SmartThings의 washerOperatingState/dryerOperatingState capability가 보고하는
 * machineState 값.
 *
 * <p>
 * SmartThings는 이 값을 소문자 문자열("run" | "pause" | "stop")로 내려주며, 기기가 해당 capability를
 * 노출하지 않거나 응답이 비어 있을 수 있다. 그런 경우를 호출 측에서 null 검사로 흩어 처리하지 않도록
 * {@link #UNKNOWN}으로 흡수한다.
 *
 * <p>
 * API 응답에는 {@link JsonValue}를 통해 SmartThings와 동일한 원시 소문자 값으로 직렬화된다
 * ({@link #UNKNOWN}은 null). 내부 판정만 enum으로 하고 클라이언트가 보던 계약은 그대로 유지하기 위함이다.
 */
@Getter
@AllArgsConstructor
public enum MachineOperatingState {
    RUN("run", "작동 중"), PAUSE("pause", "일시정지"), STOP("stop", "정지"), UNKNOWN(null, "확인 불가");

    @JsonValue
    private final String value;
    private final String description;

    /**
     * SmartThings가 내려준 원시 machineState 문자열을 enum으로 변환한다. 값이 없거나 알 수 없는 값이면
     * {@link #UNKNOWN}을 반환한다.
     */
    public static MachineOperatingState from(final String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return UNKNOWN;
        }
        return Arrays.stream(values()).filter(state -> rawValue.equalsIgnoreCase(state.value)).findFirst()
                .orElse(UNKNOWN);
    }

    /**
     * 기기가 사이클을 물리적으로 점유 중인 상태(run 또는 pause)인지 여부를 반환한다.
     */
    public boolean isOperating() {
        return this == RUN || this == PAUSE;
    }
}
