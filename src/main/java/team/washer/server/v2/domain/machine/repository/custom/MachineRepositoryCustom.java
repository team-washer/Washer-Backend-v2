package team.washer.server.v2.domain.machine.repository.custom;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import team.washer.server.v2.domain.machine.entity.Machine;
import team.washer.server.v2.domain.machine.enums.MachineStatus;
import team.washer.server.v2.domain.machine.enums.MachineType;

public interface MachineRepositoryCustom {
    Page<Machine> findAllWithFilters(String name,
            MachineType type,
            Integer floor,
            MachineStatus status,
            Pageable pageable);
}
