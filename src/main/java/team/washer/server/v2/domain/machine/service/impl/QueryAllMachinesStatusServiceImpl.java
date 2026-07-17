package team.washer.server.v2.domain.machine.service.impl;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import team.themoment.sdk.exception.ExpectedException;
import team.washer.server.v2.domain.machine.dto.response.MachineStatusResDto;
import team.washer.server.v2.domain.machine.entity.Machine;
import team.washer.server.v2.domain.machine.enums.MachineAvailability;
import team.washer.server.v2.domain.machine.repository.MachineRepository;
import team.washer.server.v2.domain.machine.service.QueryAllMachinesStatusService;
import team.washer.server.v2.domain.reservation.entity.Reservation;
import team.washer.server.v2.domain.reservation.repository.ReservationRepository;
import team.washer.server.v2.domain.smartthings.dto.response.SmartThingsDeviceStatusResDto;
import team.washer.server.v2.domain.smartthings.support.DeviceStatusQuerySupport;
import team.washer.server.v2.domain.smartthings.support.MachineStateDetectionSupport;
import team.washer.server.v2.domain.user.repository.UserRepository;
import team.washer.server.v2.global.util.DateTimeUtil;

@Service
@RequiredArgsConstructor
@Slf4j
public class QueryAllMachinesStatusServiceImpl implements QueryAllMachinesStatusService {

    private static final Sort DEFAULT_SORT = Sort
            .by(Order.asc("floor"), Order.desc("type"), Order.asc("position"), Order.asc("number"));

    private final MachineRepository machineRepository;
    private final ReservationRepository reservationRepository;
    private final DeviceStatusQuerySupport deviceStatusQuerySupport;
    private final MachineStateDetectionSupport machineStateDetectionSupport;
    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public List<MachineStatusResDto> execute(Long userId, boolean sorted) {
        final var user = userRepository.findById(userId)
                .orElseThrow(() -> new ExpectedException("사용자를 찾을 수 없습니다.", HttpStatus.NOT_FOUND));

        user.validateFloorRestriction();

        log.info("Querying all machines status");

        var machines = sorted ? machineRepository.findAll(DEFAULT_SORT) : machineRepository.findAll();
        var deviceIds = machines.stream().map(Machine::getDeviceId).toList();

        var deviceStatusMap = deviceStatusQuerySupport.queryAllDevicesStatus(deviceIds);

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
            reservation = getDisplayReservation(machine, deviceStatus, reservation);

            if (reservation != null) {
                var completionTimeStr = deviceStatus.getCompletionTime(machine.isWasher());
                if (completionTimeStr != null && !completionTimeStr.isBlank()) {
                    expectedCompletionTime = DateTimeUtil.parseAndConvertToKoreaTime(completionTimeStr);
                    if (expectedCompletionTime != null) {
                        remainingMinutes = calculateRemainingMinutes(expectedCompletionTime);
                    }
                }
            }
        }

        return new MachineStatusResDto(machine.getId(),
                machine.getName(),
                machine.getType(),
                machine.getStatus(),
                computeAvailability(machine, reservation, deviceStatus),
                operatingState,
                jobState,
                switchStatus,
                expectedCompletionTime,
                remainingMinutes,
                reservation != null ? reservation.getId() : null,
                reservation != null ? reservation.getUser().getId() : null,
                reservation != null ? reservation.getUser().getRoomNumber() : null);
    }

    private Reservation getDisplayReservation(Machine machine,
            SmartThingsDeviceStatusResDto deviceStatus,
            Reservation reservation) {
        if (reservation == null || !reservation.isRunning()) {
            return reservation;
        }
        return machineStateDetectionSupport.isCompleted(deviceStatus, machine.isWasher()).isPresent()
                ? null
                : reservation;
    }

    /**
     * 예약 정보와 실제 기기 작동 상태를 기반으로 가용성을 동적으로 계산한다. 예약 상태를 우선 source of truth로 사용하되, 예약이
     * 없어도 SmartThings에서 실제 작동 중(무단 사용)이면 IN_USE로 표시해 중복 예약을 차단한다.
     */
    private MachineAvailability computeAvailability(Machine machine,
            Reservation reservation,
            SmartThingsDeviceStatusResDto deviceStatus) {
        if (machine.getAvailability() == MachineAvailability.UNAVAILABLE) {
            return MachineAvailability.UNAVAILABLE;
        }
        if (reservation == null) {
            return isOperating(machine, deviceStatus) ? MachineAvailability.IN_USE : MachineAvailability.AVAILABLE;
        }
        return switch (reservation.getStatus()) {
            case RUNNING -> MachineAvailability.IN_USE;
            case RESERVED -> MachineAvailability.RESERVED;
            default -> throw new IllegalStateException("활성 예약의 상태가 유효하지 않습니다: " + reservation.getStatus());
        };
    }

    /**
     * 기기가 물리적으로 작동 중(run 또는 pause)인지 판정한다. 세탁기/건조기는 타입에 맞는 machineState를 본다.
     */
    private boolean isOperating(Machine machine, SmartThingsDeviceStatusResDto deviceStatus) {
        if (deviceStatus == null) {
            return false;
        }
        String machineState = null;
        if (machine.isWasher()) {
            machineState = deviceStatus.getWasherOperatingState();
        } else if (machine.isDryer()) {
            machineState = deviceStatus.getDryerOperatingState();
        }
        return "run".equalsIgnoreCase(machineState) || "pause".equalsIgnoreCase(machineState);
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
        var now = DateTimeUtil.nowInKorea();
        var duration = Duration.between(now, completionTime);
        return Math.max(0, duration.toMinutes());
    }
}
