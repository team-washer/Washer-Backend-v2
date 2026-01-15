package team.washer.server.v2.domain.machine.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum MachineAvailability {
    AVAILABLE("사용 가능"), IN_USE("사용 중"), RESERVED("예약됨"), UNAVAILABLE("사용 불가");

    private final String description;
}
