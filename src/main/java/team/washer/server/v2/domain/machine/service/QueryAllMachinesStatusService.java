package team.washer.server.v2.domain.machine.service;

import java.util.List;

import team.washer.server.v2.domain.machine.dto.response.MachineStatusResDto;

public interface QueryAllMachinesStatusService {
    List<MachineStatusResDto> execute();
}
