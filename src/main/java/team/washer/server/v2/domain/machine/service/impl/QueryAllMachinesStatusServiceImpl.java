package team.washer.server.v2.domain.machine.service.impl;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import team.themoment.sdk.exception.ExpectedException;
import team.washer.server.v2.domain.machine.dto.response.MachineStatusResDto;
import team.washer.server.v2.domain.machine.entity.Machine;
import team.washer.server.v2.domain.machine.enums.MachineAvailability;
import team.washer.server.v2.domain.machine.enums.MachineStatus;
import team.washer.server.v2.domain.machine.enums.MachineType;
import team.washer.server.v2.domain.machine.repository.MachineRepository;
import team.washer.server.v2.domain.machine.service.QueryAllMachinesStatusService;
import team.washer.server.v2.domain.reservation.entity.Reservation;
import team.washer.server.v2.domain.reservation.repository.ReservationRepository;
import team.washer.server.v2.domain.smartthings.dto.response.SmartThingsDeviceStatusResDto;
import team.washer.server.v2.domain.smartthings.enums.MachineOperatingState;
import team.washer.server.v2.domain.smartthings.support.DeviceStatusQuerySupport;
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
    private final UserRepository userRepository;
    private final PlatformTransactionManager transactionManager;

    @Override
    public List<MachineStatusResDto> execute(Long userId, boolean sorted) {
        final var machineSnapshots = loadMachineSnapshots(userId, sorted);

        log.info("Querying all machines status");

        final var deviceIds = machineSnapshots.stream().map(MachineSnapshot::deviceId).toList();
        final var deviceStatusMap = deviceStatusQuerySupport.queryAllDevicesStatus(deviceIds);
        final var currentStates = loadCurrentStates(machineSnapshots);

        final var results = machineSnapshots.stream().map(machine -> currentStates.get(machine.id()))
                .filter(currentState -> currentState != null).map(currentState -> {
                    final var deviceStatus = currentState.externalStatusApplicable()
                            ? deviceStatusMap.get(currentState.queriedDeviceId())
                            : null;
                    return mapToStatusDto(currentState.machine(), deviceStatus, currentState.reservation());
                }).toList();

        log.info("Successfully queried status for {} machines", results.size());

        return results;
    }

    private List<MachineSnapshot> loadMachineSnapshots(Long userId, boolean sorted) {
        return executeReadOnly(status -> {
            final var user = userRepository.findById(userId)
                    .orElseThrow(() -> new ExpectedException("사용자를 찾을 수 없습니다.", HttpStatus.NOT_FOUND));

            user.validateFloorRestriction();

            final var machines = sorted ? machineRepository.findAll(DEFAULT_SORT) : machineRepository.findAll();
            return machines.stream().map(MachineSnapshot::from).toList();
        });
    }

    private Map<Long, CurrentMachineState> loadCurrentStates(List<MachineSnapshot> machineSnapshots) {
        return executeReadOnly(status -> {
            final var machineIds = machineSnapshots.stream().map(MachineSnapshot::id).toList();
            final var machinesById = machineRepository.findAllById(machineIds).stream()
                    .collect(Collectors.toMap(Machine::getId, Function.identity()));
            final var currentStates = new HashMap<Long, CurrentMachineState>();
            for (final var machine : machineSnapshots) {
                final var currentMachine = machinesById.get(machine.id());
                if (currentMachine == null) {
                    continue;
                }
                final var reservation = reservationRepository.findCurrentlyActiveReservationByMachineId(machine.id())
                        .orElse(null);
                final var currentSnapshot = MachineSnapshot.from(currentMachine);
                currentStates.put(machine.id(),
                        new CurrentMachineState(currentSnapshot,
                                reservation,
                                machine.deviceId(),
                                Objects.equals(machine.deviceId(), currentSnapshot.deviceId())
                                        && machine.washer() == currentSnapshot.washer()));
            }
            return currentStates;
        });
    }

    private <T> T executeReadOnly(Function<TransactionStatus, T> callback) {
        final var transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.setReadOnly(true);
        return transactionTemplate.execute(callback::apply);
    }

    private MachineStatusResDto mapToStatusDto(MachineSnapshot machine,
            SmartThingsDeviceStatusResDto deviceStatus,
            Reservation reservation) {
        MachineOperatingState operatingState = MachineOperatingState.UNKNOWN;
        String jobState = null;
        String switchStatus = null;
        LocalDateTime expectedCompletionTime = null;
        Long remainingMinutes = null;

        if (deviceStatus != null) {
            operatingState = deviceStatus.getOperatingState(machine.washer());
            jobState = deviceStatus.getJobState(machine.washer());
            switchStatus = deviceStatus.getSwitchStatus();

            if (reservation != null) {
                var completionTimeStr = deviceStatus.getCompletionTime(machine.washer());
                if (completionTimeStr != null && !completionTimeStr.isBlank()) {
                    expectedCompletionTime = DateTimeUtil.parseAndConvertToKoreaTime(completionTimeStr);
                    if (expectedCompletionTime != null) {
                        remainingMinutes = calculateRemainingMinutes(expectedCompletionTime);
                    }
                }
            }
        }

        return new MachineStatusResDto(machine.id(),
                machine.name(),
                machine.type(),
                machine.status(),
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

    /**
     * 예약 정보와 실제 기기 작동 상태를 기반으로 가용성을 동적으로 계산한다. 예약 상태를 유일한 source of truth로 사용하되,
     * 예약이 없어도 SmartThings에서 실제 작동 중(무단 사용)이면 IN_USE로 표시해 중복 예약을 차단한다. 사용 불가와 통세척 중
     * 상태는 DB에 저장된 관리 상태를 우선한다.
     *
     * <p>
     * 완료 여부를 이 API에서 따로 예측하지 않는다. 완료 확정은 라이프사이클 스케줄러가 디바운스와 가드를 거쳐 DB에 반영하며, 목록은 그
     * 결과만 그대로 보여준다. 화면에 표시된 상태와 DB에 저장된 상태가 갈라지지 않게 하기 위함이다.
     */
    private MachineAvailability computeAvailability(MachineSnapshot machine,
            Reservation reservation,
            SmartThingsDeviceStatusResDto deviceStatus) {
        if (machine.availability() == MachineAvailability.UNAVAILABLE
                || machine.availability() == MachineAvailability.CLEANING) {
            return machine.availability();
        }
        if (reservation == null) {
            if (deviceStatus == null) {
                return machine.availability() == MachineAvailability.RESERVED
                        ? MachineAvailability.AVAILABLE
                        : machine.availability();
            }
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
    private boolean isOperating(MachineSnapshot machine, SmartThingsDeviceStatusResDto deviceStatus) {
        return deviceStatus != null && deviceStatus.getOperatingState(machine.washer()).isOperating();
    }

    private Long calculateRemainingMinutes(LocalDateTime completionTime) {
        var now = DateTimeUtil.nowInKorea();
        var duration = Duration.between(now, completionTime);
        return Math.max(0, duration.toMinutes());
    }

    private record MachineSnapshot(Long id, String name, MachineType type, MachineStatus status,
            MachineAvailability availability, String deviceId, boolean washer) {

        private static MachineSnapshot from(Machine machine) {
            return new MachineSnapshot(machine.getId(),
                    machine.getName(),
                    machine.getType(),
                    machine.getStatus(),
                    machine.getAvailability(),
                    machine.getDeviceId(),
                    machine.isWasher());
        }
    }

    private record CurrentMachineState(MachineSnapshot machine, Reservation reservation, String queriedDeviceId,
            boolean externalStatusApplicable) {
    }
}
