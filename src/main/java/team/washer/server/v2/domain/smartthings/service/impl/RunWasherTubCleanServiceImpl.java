package team.washer.server.v2.domain.smartthings.service.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import team.washer.server.v2.domain.machine.entity.Machine;
import team.washer.server.v2.domain.machine.enums.MachineAvailability;
import team.washer.server.v2.domain.machine.enums.MachineStatus;
import team.washer.server.v2.domain.machine.enums.MachineType;
import team.washer.server.v2.domain.machine.repository.MachineRepository;
import team.washer.server.v2.domain.reservation.enums.ReservationStatus;
import team.washer.server.v2.domain.reservation.repository.ReservationRepository;
import team.washer.server.v2.domain.smartthings.dto.request.SmartThingsCommandReqDto;
import team.washer.server.v2.domain.smartthings.dto.response.SmartThingsDeviceStatusResDto;
import team.washer.server.v2.domain.smartthings.enums.MachineOperatingState;
import team.washer.server.v2.domain.smartthings.exception.SmartThingsPermissionException;
import team.washer.server.v2.domain.smartthings.service.RunWasherTubCleanService;
import team.washer.server.v2.domain.smartthings.service.SendDeviceCommandService;
import team.washer.server.v2.domain.smartthings.support.DeviceStatusQuerySupport;
import team.washer.server.v2.domain.smartthings.support.WasherTubCleanMachineGuard;
import team.washer.server.v2.global.thirdparty.smartthings.config.SmartThingsTubCleanEnvironment;

@Service
@RequiredArgsConstructor
@Slf4j
public class RunWasherTubCleanServiceImpl implements RunWasherTubCleanService {

    private static final List<ReservationStatus> ACTIVE_STATUSES = List.of(ReservationStatus.RESERVED,
            ReservationStatus.RUNNING);

    private final MachineRepository machineRepository;
    private final ReservationRepository reservationRepository;
    private final DeviceStatusQuerySupport deviceStatusQuerySupport;
    private final SendDeviceCommandService sendDeviceCommandService;
    private final WasherTubCleanMachineGuard machineGuard;
    private final SmartThingsTubCleanEnvironment tubCleanEnvironment;

    @Override
    public void execute() {
        if (!tubCleanEnvironment.hasCycle()) {
            log.error("washer tub clean skipped because cycle is not configured");
            return;
        }

        var candidates = findCandidates();
        if (candidates.isEmpty()) {
            log.info("washer tub clean skipped because no washer is available");
            return;
        }

        var statusMap = deviceStatusQuerySupport
                .queryAllDevicesStatus(candidates.stream().map(Machine::getDeviceId).toList());
        var started = new ArrayList<String>();
        var skipped = new ArrayList<String>();
        var failed = new ArrayList<String>();

        for (var machine : candidates) {
            try {
                var status = statusMap.get(machine.getDeviceId());
                if (!canStart(machine, status)) {
                    skipped.add(machine.getName());
                    continue;
                }

                var target = machineGuard.occupyIfAvailable(machine.getId());
                if (target.isEmpty()) {
                    skipped.add(machine.getName());
                    continue;
                }

                sendCommand(target.get());
                started.add(target.get().machineName());
            } catch (SmartThingsPermissionException e) {
                log.warn("washer tub clean stopped because SmartThings permission is denied machine={} reason={}",
                        machine.getName(),
                        e.getMessage());
                break;
            } catch (Exception e) {
                failed.add(machine.getName());
                log.error("washer tub clean failed machine={} reason={}", machine.getName(), e.getMessage(), e);
            }
        }

        log.info("washer tub clean batch completed started={} skipped={} failed={}", started, skipped, failed);
    }

    private List<Machine> findCandidates() {
        var activeMachineIds = Set.copyOf(reservationRepository.findMachineIdsByStatusIn(ACTIVE_STATUSES));
        return machineRepository
                .findByTypeAndStatusAndAvailability(MachineType.WASHER,
                        MachineStatus.NORMAL,
                        MachineAvailability.AVAILABLE)
                .stream().filter(machine -> !activeMachineIds.contains(machine.getId()))
                .filter(machine -> machine.getDeviceId() != null && !machine.getDeviceId().isBlank()).toList();
    }

    private boolean canStart(Machine machine, SmartThingsDeviceStatusResDto status) {
        if (status == null) {
            log.warn("washer tub clean skipped because device status is unknown machine={} deviceId={}",
                    machine.getName(),
                    machine.getDeviceId());
            return false;
        }
        if (!status.isRemoteControlEnabled()) {
            log.info("washer tub clean skipped because remote control is disabled machine={} deviceId={}",
                    machine.getName(),
                    machine.getDeviceId());
            return false;
        }
        if (status.isSwitchOff() || status.getOperatingState(true) != MachineOperatingState.STOP
                || status.isJobStateActive(true)) {
            log.info(
                    "washer tub clean skipped because washer is not idle machine={} deviceId={} machineState={} jobState={}",
                    machine.getName(),
                    machine.getDeviceId(),
                    status.getWasherOperatingState(),
                    status.getWasherJobState());
            return false;
        }
        return true;
    }

    private void sendCommand(WasherTubCleanMachineGuard.TubCleanTarget target) {
        try {
            sendDeviceCommandService.execute(target.deviceId(),
                    SmartThingsCommandReqDto.washerTubClean(tubCleanEnvironment.cycle()));
            log.info("washer tub clean command sent machine={} deviceId={}", target.machineName(), target.deviceId());
        } catch (Exception e) {
            machineGuard.releaseIfNoActiveReservation(target.machineId());
            throw e;
        }
    }
}
