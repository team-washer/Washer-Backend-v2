package team.washer.server.v2.domain.machine.service;

import org.springframework.data.domain.Pageable;

import team.washer.server.v2.domain.machine.dto.response.MachineListResDto;
import team.washer.server.v2.domain.machine.enums.MachineStatus;
import team.washer.server.v2.domain.machine.enums.MachineType;

public interface QueryAllMachinesService {
    MachineListResDto execute(String name, MachineType type, Integer floor, MachineStatus status, Pageable pageable);
}
