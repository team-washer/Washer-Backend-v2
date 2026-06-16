package team.washer.server.v2.domain.machine.service.impl;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import team.themoment.sdk.exception.ExpectedException;
import team.washer.server.v2.domain.machine.dto.response.ForceStopMachineResDto;
import team.washer.server.v2.domain.machine.entity.Machine;
import team.washer.server.v2.domain.machine.enums.ForceStopResult;
import team.washer.server.v2.domain.machine.enums.MachineAvailability;
import team.washer.server.v2.domain.machine.enums.MachineStatus;
import team.washer.server.v2.domain.machine.enums.MachineType;
import team.washer.server.v2.domain.machine.repository.MachineRepository;
import team.washer.server.v2.domain.machine.service.ForceStopMachineService;
import team.washer.server.v2.domain.reservation.entity.Reservation;
import team.washer.server.v2.domain.reservation.repository.ReservationRepository;
import team.washer.server.v2.domain.smartthings.dto.request.SmartThingsCommandReqDto;
import team.washer.server.v2.domain.smartthings.dto.response.SmartThingsDeviceStatusResDto;
import team.washer.server.v2.domain.smartthings.service.SendDeviceCommandService;
import team.washer.server.v2.domain.smartthings.support.DeviceStatusQuerySupport;

@Service
@RequiredArgsConstructor
@Slf4j
public class ForceStopMachineServiceImpl implements ForceStopMachineService {

    private final MachineRepository machineRepository;
    private final ReservationRepository reservationRepository;
    private final DeviceStatusQuerySupport deviceStatusQuerySupport;
    private final SendDeviceCommandService sendDeviceCommandService;
    private final TransactionTemplate transactionTemplate;

    @Override
    public ForceStopMachineResDto execute(Long machineId) {
        final var machine = machineRepository.findById(machineId)
                .orElseThrow(() -> new ExpectedException("기기를 찾을 수 없습니다", HttpStatus.NOT_FOUND));
        final var status = deviceStatusQuerySupport.queryDeviceStatus(machine.getDeviceId());
        final var previousMachineState = extractMachineState(machine, status);
        final var forceStopResult = forceStop(machine, status, previousMachineState);
        final var updateResult = updateMachineAndReservation(machineId, forceStopResult);

        log.info("machine force stop processed machineId={} deviceId={} result={} cancelledReservationId={}",
                updateResult.machineId(),
                updateResult.deviceId(),
                forceStopResult,
                updateResult.cancelledReservationId());

        return new ForceStopMachineResDto(updateResult.machineId(),
                updateResult.machineName(),
                updateResult.machineType(),
                updateResult.deviceId(),
                forceStopResult,
                previousMachineState,
                updateResult.cancelledReservationId(),
                updateResult.cancelledReservationId() != null,
                updateResult.availability());
    }

    private ForceStopResult forceStop(Machine machine, SmartThingsDeviceStatusResDto status, String machineState) {
        if (status == null) {
            throw new ExpectedException("기기 상태를 확인할 수 없습니다", HttpStatus.BAD_GATEWAY);
        }
        if ("off".equalsIgnoreCase(status.getSwitchStatus()) || "stop".equalsIgnoreCase(machineState)) {
            return ForceStopResult.ALREADY_STOPPED;
        }
        if (!"run".equalsIgnoreCase(machineState) && !"pause".equalsIgnoreCase(machineState)) {
            throw new ExpectedException("기기 동작 상태를 확인할 수 없습니다", HttpStatus.BAD_GATEWAY);
        }

        if (machine.isWasher()) {
            sendDeviceCommandService.execute(machine.getDeviceId(), SmartThingsCommandReqDto.stopWasher());
            return ForceStopResult.STOPPED;
        }
        if (machine.isDryer()) {
            sendDeviceCommandService.execute(machine.getDeviceId(), SmartThingsCommandReqDto.stopDryer());
            return ForceStopResult.STOPPED;
        }
        throw new ExpectedException("지원하지 않는 기기 유형입니다", HttpStatus.BAD_REQUEST);
    }

    private UpdateResult updateMachineAndReservation(Long machineId, ForceStopResult forceStopResult) {
        return transactionTemplate.execute(status -> {
            final var machine = machineRepository.findByIdForUpdate(machineId)
                    .orElseThrow(() -> new ExpectedException("기기를 찾을 수 없습니다", HttpStatus.NOT_FOUND));
            final var activeReservation = reservationRepository.findActiveReservationByMachineId(machineId);
            final var cancelledReservationId = cancelActiveReservationIfNeeded(activeReservation.orElse(null),
                    forceStopResult);

            syncMachineAvailability(machine, activeReservation.orElse(null), cancelledReservationId != null);
            final var savedMachine = machineRepository.save(machine);

            return new UpdateResult(savedMachine.getId(),
                    savedMachine.getName(),
                    savedMachine.getType(),
                    savedMachine.getDeviceId(),
                    savedMachine.getAvailability(),
                    cancelledReservationId);
        });
    }

    private Long cancelActiveReservationIfNeeded(Reservation reservation, ForceStopResult forceStopResult) {
        if (reservation == null) {
            return null;
        }
        if (forceStopResult != ForceStopResult.STOPPED && !reservation.isRunning()) {
            return null;
        }
        reservation.cancel();
        reservationRepository.save(reservation);
        return reservation.getId();
    }

    private void syncMachineAvailability(Machine machine, Reservation activeReservation, boolean reservationCancelled) {
        if (machine.getStatus() != MachineStatus.NORMAL) {
            machine.markAsUnavailable();
            return;
        }
        if (!reservationCancelled && activeReservation != null) {
            if (activeReservation.isReserved()) {
                machine.markAsReserved();
                return;
            }
            if (activeReservation.isRunning()) {
                machine.markAsInUse();
                return;
            }
        }
        machine.markAsAvailable();
    }

    private String extractMachineState(Machine machine, SmartThingsDeviceStatusResDto status) {
        if (status == null) {
            return null;
        }
        if (machine.isWasher()) {
            return status.getWasherOperatingState();
        }
        if (machine.isDryer()) {
            return status.getDryerOperatingState();
        }
        return null;
    }

    private record UpdateResult(Long machineId, String machineName, MachineType machineType, String deviceId,
            MachineAvailability availability, Long cancelledReservationId) {
    }
}
