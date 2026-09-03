package team.washer.server.v2.domain.smartthings.support;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import team.washer.server.v2.domain.machine.entity.Machine;
import team.washer.server.v2.domain.machine.enums.MachineAvailability;
import team.washer.server.v2.domain.machine.repository.MachineRepository;
import team.washer.server.v2.domain.reservation.enums.ReservationStatus;
import team.washer.server.v2.domain.reservation.repository.ReservationRepository;

/**
 * 무세제 통세척 실행과 예약 생성이 충돌하지 않도록 기기 점유를 관리한다.
 */
@Component
@RequiredArgsConstructor
public class WasherTubCleanMachineGuard {

    private static final List<ReservationStatus> ACTIVE_STATUSES = List.of(ReservationStatus.RESERVED,
            ReservationStatus.RUNNING);

    private final MachineRepository machineRepository;
    private final ReservationRepository reservationRepository;

    /**
     * 기기를 잠근 뒤 실행 조건을 다시 확인하고 통세척 실행 중으로 점유한다.
     *
     * @param machineId
     *            점유할 세탁기 ID
     * @return 점유된 세탁기의 식별 정보. 조건이 맞지 않으면 빈 값
     */
    @Transactional
    public Optional<TubCleanTarget> occupyIfAvailable(Long machineId) {
        var machine = machineRepository.findByIdForUpdate(machineId).orElse(null);
        if (machine == null || !machine.isWasher() || !machine.isAvailable() || hasActiveReservation(machine)) {
            return Optional.empty();
        }

        machine.markAsCleaning();
        return Optional.of(new TubCleanTarget(machine.getId(), machine.getName(), machine.getDeviceId()));
    }

    /**
     * 활성 예약이 없는 통세척 점유 상태를 해제한다.
     *
     * @param machineId
     *            해제할 세탁기 ID
     * @return 해제 여부
     */
    @Transactional
    public boolean releaseIfNoActiveReservation(Long machineId) {
        var machine = machineRepository.findByIdForUpdate(machineId).orElse(null);
        if (machine == null || !machine.isWasher() || hasActiveReservation(machine)
                || machine.getAvailability() != MachineAvailability.CLEANING) {
            return false;
        }

        machine.finishCleaning();
        return true;
    }

    private boolean hasActiveReservation(Machine machine) {
        return reservationRepository.countActiveReservationsByMachine(machine, ACTIVE_STATUSES) > 0;
    }

    /**
     * 트랜잭션 밖에서 SmartThings 명령에 사용할 세탁기 식별 정보.
     */
    public record TubCleanTarget(Long machineId, String machineName, String deviceId) {
    }
}
