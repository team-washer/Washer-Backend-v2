package team.washer.server.v2.domain.machine.repository.custom;

import java.util.List;

import team.washer.server.v2.domain.machine.entity.Machine;
import team.washer.server.v2.domain.machine.enums.MachineStatus;
import team.washer.server.v2.domain.machine.enums.MachineType;

public interface MachineRepositoryCustom {
    List<Machine> findAllWithFilters(String name, MachineType type, Integer floor, MachineStatus status);
}
