package team.washer.server.v2.domain.machine.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum Position {
    LEFT("L", "왼쪽"), RIGHT("R", "오른쪽");

    private final String code;
    private final String description;
}
