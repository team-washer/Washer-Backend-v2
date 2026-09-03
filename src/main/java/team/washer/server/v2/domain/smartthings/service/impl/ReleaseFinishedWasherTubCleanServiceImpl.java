package team.washer.server.v2.domain.smartthings.service.impl;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import team.washer.server.v2.domain.machine.entity.Machine;
import team.washer.server.v2.domain.machine.enums.MachineAvailability;
import team.washer.server.v2.domain.machine.enums.MachineType;
import team.washer.server.v2.domain.machine.repository.MachineRepository;
import team.washer.server.v2.domain.reservation.enums.ReservationStatus;
import team.washer.server.v2.domain.reservation.repository.ReservationRepository;
import team.washer.server.v2.domain.smartthings.dto.response.SmartThingsDeviceStatusResDto;
import team.washer.server.v2.domain.smartthings.enums.MachineOperatingState;
import team.washer.server.v2.domain.smartthings.service.ReleaseFinishedWasherTubCleanService;
import team.washer.server.v2.domain.smartthings.support.DeviceStatusQuerySupport;
import team.washer.server.v2.domain.smartthings.support.WasherTubCleanMachineGuard;
import team.washer.server.v2.global.util.DateTimeUtil;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReleaseFinishedWasherTubCleanServiceImpl implements ReleaseFinishedWasherTubCleanService {

    private static final List<ReservationStatus> ACTIVE_STATUSES = List.of(ReservationStatus.RESERVED,
            ReservationStatus.RUNNING);
    private static final long COMMAND_START_GRACE_MINUTES = 5;

    private final MachineRepository machineRepository;
    private final ReservationRepository reservationRepository;
    private final DeviceStatusQuerySupport deviceStatusQuerySupport;
    private final WasherTubCleanMachineGuard machineGuard;

    @Override
    public void execute() {
        var candidates = findCandidates(DateTimeUtil.nowInKorea());
        if (candidates.isEmpty()) {
            return;
        }

        var statusMap = deviceStatusQuerySupport
                .queryAllDevicesStatus(candidates.stream().map(Machine::getDeviceId).toList());
        for (var machine : candidates) {
            var status = statusMap.get(machine.getDeviceId());
            if (!isFinished(status)) {
                continue;
            }
            if (machineGuard.releaseIfNoActiveReservation(machine.getId())) {
                log.info("washer tub clean occupancy released machine={} deviceId={}",
                        machine.getName(),
                        machine.getDeviceId());
            }
        }
    }

    private List<Machine> findCandidates(LocalDateTime now) {
        var activeMachineIds = Set.copyOf(reservationRepository.findMachineIdsByStatusIn(ACTIVE_STATUSES));
        var graceThreshold = now.minusMinutes(COMMAND_START_GRACE_MINUTES);
        return machineRepository.findByTypeAndAvailability(MachineType.WASHER, MachineAvailability.CLEANING).stream()
                .filter(machine -> !activeMachineIds.contains(machine.getId()))
                .filter(machine -> machine.getDeviceId() != null && !machine.getDeviceId().isBlank())
                .filter(machine -> machine.getUpdatedAt() == null || !machine.getUpdatedAt().isAfter(graceThreshold))
                .toList();
    }

    private boolean isFinished(SmartThingsDeviceStatusResDto status) {
        return status != null && status.getOperatingState(true) == MachineOperatingState.STOP
                && !status.isJobStateActive(true);
    }
}
