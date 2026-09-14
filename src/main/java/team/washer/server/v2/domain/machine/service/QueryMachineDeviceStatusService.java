package team.washer.server.v2.domain.machine.service;

import team.washer.server.v2.domain.machine.dto.response.MachineDeviceStatusResDto;

public interface QueryMachineDeviceStatusService {
    MachineDeviceStatusResDto execute(Long userId, Long machineId);
}
