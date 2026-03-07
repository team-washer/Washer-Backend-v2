package team.washer.server.v2.domain.machine.service;

import team.washer.server.v2.domain.machine.dto.request.UpdateMachineReqDto;
import team.washer.server.v2.domain.machine.dto.response.MachineResDto;

public interface UpdateMachineService {
    MachineResDto execute(Long machineId, UpdateMachineReqDto request);
}
