package team.washer.server.v2.domain.machine.service;

import team.washer.server.v2.domain.machine.dto.response.MachineStatusUpdateResDto;
import team.washer.server.v2.domain.machine.enums.MachineStatus;

public interface UpdateMachineStatusService {
    MachineStatusUpdateResDto execute(Long machineId, MachineStatus status);
}
