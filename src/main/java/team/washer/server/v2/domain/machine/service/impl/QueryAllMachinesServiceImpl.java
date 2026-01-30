package team.washer.server.v2.domain.machine.service.impl;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import team.washer.server.v2.domain.machine.dto.response.MachineListResDto;
import team.washer.server.v2.domain.machine.dto.response.MachineResDto;
import team.washer.server.v2.domain.machine.entity.Machine;
import team.washer.server.v2.domain.machine.enums.MachineStatus;
import team.washer.server.v2.domain.machine.enums.MachineType;
import team.washer.server.v2.domain.machine.repository.MachineRepository;
import team.washer.server.v2.domain.machine.service.QueryAllMachinesService;

@Service
@RequiredArgsConstructor
public class QueryAllMachinesServiceImpl implements QueryAllMachinesService {

    private final MachineRepository machineRepository;

    @Transactional(readOnly = true)
    @Override
    public MachineListResDto execute(String name,
            MachineType type,
            Integer floor,
            MachineStatus status,
            Pageable pageable) {
        final var machinesPage = machineRepository.findAllWithFilters(name, type, floor, status, pageable);
        final var machineDtos = machinesPage.getContent().stream().map(this::toMachineResDto).toList();

        return new MachineListResDto(machineDtos,
                machinesPage.getTotalElements(),
                machinesPage.getTotalPages(),
                machinesPage.getNumber());
    }

    private MachineResDto toMachineResDto(Machine machine) {
        return new MachineResDto(machine.getId(),
                machine.getName(),
                machine.getType(),
                machine.getFloor(),
                machine.getPosition(),
                machine.getNumber(),
                machine.getStatus(),
                machine.getAvailability(),
                machine.getDeviceId());
    }
}
