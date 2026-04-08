package team.washer.server.v2.domain.machine.service.impl;

import java.util.Objects;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import team.themoment.sdk.exception.ExpectedException;
import team.washer.server.v2.domain.machine.dto.request.UpdateMachineReqDto;
import team.washer.server.v2.domain.machine.dto.response.MachineResDto;
import team.washer.server.v2.domain.machine.entity.Machine;
import team.washer.server.v2.domain.machine.enums.MachineType;
import team.washer.server.v2.domain.machine.enums.Position;
import team.washer.server.v2.domain.machine.repository.MachineRepository;
import team.washer.server.v2.domain.machine.service.UpdateMachineService;

@Service
@RequiredArgsConstructor
public class UpdateMachineServiceImpl implements UpdateMachineService {

    private final MachineRepository machineRepository;

    @Transactional
    @Override
    public MachineResDto execute(Long machineId, UpdateMachineReqDto request) {
        final var machine = machineRepository.findById(machineId)
                .orElseThrow(() -> new ExpectedException("기기를 찾을 수 없습니다", HttpStatus.NOT_FOUND));

        if (request.deviceId() != null) {
            validateDeviceIdUnique(machine, request.deviceId());
            machine.updateDeviceId(request.deviceId());
        }

        if (hasLocationChange(request)) {
            final var newType = request.type() != null ? request.type() : machine.getType();
            final var newFloor = request.floor() != null ? request.floor() : machine.getFloor();
            final var newPosition = request.position() != null ? request.position() : machine.getPosition();
            final var newNumber = request.number() != null ? request.number() : machine.getNumber();

            validateLocationUnique(machine, newType, newFloor, newPosition, newNumber);
            machine.updateLocation(newType, newFloor, newPosition, newNumber);
            machine.updateName();
        }

        return toResDto(machine);
    }

    private boolean hasLocationChange(UpdateMachineReqDto request) {
        return request.type() != null || request.floor() != null || request.position() != null
                || request.number() != null;
    }

    private void validateDeviceIdUnique(Machine machine, String newDeviceId) {
        machineRepository.findByDeviceId(newDeviceId).ifPresent(existing -> {
            if (!Objects.equals(existing.getId(), machine.getId())) {
                throw new ExpectedException("이미 다른 기기에서 사용 중인 Device ID입니다", HttpStatus.CONFLICT);
            }
        });
    }

    private void validateLocationUnique(Machine machine,
            MachineType type,
            Integer floor,
            Position position,
            Integer number) {
        machineRepository.findByLocation(type, floor, position, number).ifPresent(existing -> {
            if (!Objects.equals(existing.getId(), machine.getId())) {
                throw new ExpectedException("해당 위치에 이미 다른 기기가 등록되어 있습니다", HttpStatus.CONFLICT);
            }
        });
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
