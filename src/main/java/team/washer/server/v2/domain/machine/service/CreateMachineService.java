package team.washer.server.v2.domain.machine.service;

import team.washer.server.v2.domain.machine.dto.request.CreateMachineReqDto;
import team.washer.server.v2.domain.machine.dto.response.MachineResDto;

public interface CreateMachineService {
    MachineResDto execute(CreateMachineReqDto request);
}
