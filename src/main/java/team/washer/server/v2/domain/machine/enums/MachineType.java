package team.washer.server.v2.domain.machine.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum MachineType {
    WASHER("Washer", "세탁기"), DRYER("Dryer", "건조기");

    private final String code;
    private final String description;
}
