package team.washer.server.v2.domain.machine.service.impl;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import team.washer.server.v2.domain.machine.dto.response.MachineStatusResDto;
import team.washer.server.v2.domain.machine.entity.Machine;
import team.washer.server.v2.domain.machine.repository.MachineRepository;
import team.washer.server.v2.domain.machine.service.QueryAllMachinesStatusService;
import team.washer.server.v2.domain.reservation.entity.Reservation;
import team.washer.server.v2.domain.reservation.repository.ReservationRepository;
import team.washer.server.v2.domain.smartthings.dto.response.SmartThingsDeviceStatusResDto;
import team.washer.server.v2.domain.smartthings.service.QueryAllDevicesStatusService;
import team.washer.server.v2.global.util.DateTimeUtil;

@Service
@RequiredArgsConstructor
@Slf4j
public class QueryAllMachinesStatusServiceImpl implements QueryAllMachinesStatusService {

    private final MachineRepository machineRepository;
    private final ReservationRepository reservationRepository;
    private final QueryAllDevicesStatusService queryAllDevicesStatusService;

    @Override
    @Transactional(readOnly = true)
    public List<MachineStatusResDto> execute() {
        log.info("Querying all machines status");

        var machines = machineRepository.findAll();
        var deviceIds = machines.stream().map(Machine::getDeviceId).toList();

        var deviceStatusMap = queryAllDevicesStatusService.execute(deviceIds);

        var results = machines.stream().map(machine -> {
            var reservation = reservationRepository.findActiveReservationByMachineId(machine.getId()).orElse(null);
            return mapToStatusDto(machine, deviceStatusMap.get(machine.getDeviceId()), reservation);
        }).toList();

        log.info("Successfully queried status for {} machines", results.size());

        return results;
    }

    private MachineStatusResDto mapToStatusDto(Machine machine,
            SmartThingsDeviceStatusResDto deviceStatus,
            Reservation reservation) {
        String operatingState = null;
        String jobState = null;
        String switchStatus = null;
        LocalDateTime expectedCompletionTime = null;
        Long remainingMinutes = null;

        if (deviceStatus != null) {
            operatingState = getOperatingState(machine, deviceStatus);
            jobState = getJobState(machine, deviceStatus);
            switchStatus = deviceStatus.getSwitchStatus();

            var completionTimeStr = deviceStatus.getCompletionTime();
            if (completionTimeStr != null && !completionTimeStr.isBlank()) {
                expectedCompletionTime = DateTimeUtil.parseAndConvertToKoreaTime(completionTimeStr);
                if (expectedCompletionTime != null) {
                    remainingMinutes = calculateRemainingMinutes(expectedCompletionTime);
                }
            }
        }

        return new MachineStatusResDto(machine.getId(),
                machine.getName(),
                machine.getType(),
                machine.getStatus(),
                machine.getAvailability(),
                operatingState,
                jobState,
                switchStatus,
                expectedCompletionTime,
                remainingMinutes,
                reservation != null ? reservation.getId() : null,
                reservation != null ? reservation.getUser().getId() : null);
    }

    private String getOperatingState(Machine machine, SmartThingsDeviceStatusResDto deviceStatus) {
        if (machine.isWasher()) {
            return deviceStatus.getWasherOperatingState();
        } else if (machine.isDryer()) {
            return deviceStatus.getDryerOperatingState();
        }
        return null;
    }

    private String getJobState(Machine machine, SmartThingsDeviceStatusResDto deviceStatus) {
        if (machine.isWasher()) {
            return deviceStatus.getWasherJobState();
        } else if (machine.isDryer()) {
            return deviceStatus.getDryerJobState();
        }
        return null;
    }

    private Long calculateRemainingMinutes(LocalDateTime completionTime) {
        var now = LocalDateTime.now();
        var duration = Duration.between(now, completionTime);
        return Math.max(0, duration.toMinutes());
    }
}
