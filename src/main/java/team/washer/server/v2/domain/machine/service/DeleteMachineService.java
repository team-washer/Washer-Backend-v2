package team.washer.server.v2.domain.machine.service;

import team.washer.server.v2.domain.machine.dto.response.DeleteMachineResDto;

public interface DeleteMachineService {
    DeleteMachineResDto execute(Long machineId);
}
