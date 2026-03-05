package team.washer.server.v2.domain.machine.service.impl;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import team.themoment.sdk.exception.ExpectedException;
import team.washer.server.v2.domain.machine.dto.request.CreateMachineReqDto;
import team.washer.server.v2.domain.machine.dto.response.MachineResDto;
import team.washer.server.v2.domain.machine.entity.Machine;
import team.washer.server.v2.domain.machine.repository.MachineRepository;
import team.washer.server.v2.domain.machine.service.CreateMachineService;

@Service
@RequiredArgsConstructor
public class CreateMachineServiceImpl implements CreateMachineService {

    private final MachineRepository machineRepository;

    @Transactional
    @Override
    public MachineResDto execute(CreateMachineReqDto request) {
        if (machineRepository.existsByDeviceId(request.deviceId())) {
            throw new ExpectedException("이미 등록된 Device ID입니다", HttpStatus.CONFLICT);
        }

        if (machineRepository.findByLocation(request.type(), request.floor(), request.position(), request.number())
                .isPresent()) {
            throw new ExpectedException("해당 위치에 이미 기기가 등록되어 있습니다", HttpStatus.CONFLICT);
        }

        final var name = Machine.generateName(request.type(), request.floor(), request.position(), request.number());
        final var machine = Machine.builder().name(name).type(request.type()).floor(request.floor())
                .position(request.position()).number(request.number()).deviceId(request.deviceId()).build();
        final var saved = machineRepository.save(machine);

        return toResDto(saved);
    }

    private MachineResDto toResDto(Machine machine) {
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
