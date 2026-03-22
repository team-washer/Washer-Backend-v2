package team.washer.server.v2.domain.machine.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum MachineStatus {
    NORMAL("정상"), MALFUNCTION("고장");

    private final String description;
}
