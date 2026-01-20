package team.washer.server.v2.domain.reservation.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum CycleAction {
    ACTIVATED("Activated", "활성화됨"),
    DEACTIVATED("Deactivated", "비활성화됨");

    private final String code;
    private final String description;
}
