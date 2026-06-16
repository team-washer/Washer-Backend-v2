package team.washer.server.v2.domain.machine.service;

import team.washer.server.v2.domain.machine.dto.response.ForceStopMachineResDto;

public interface ForceStopMachineService {

    ForceStopMachineResDto execute(Long machineId);
}
