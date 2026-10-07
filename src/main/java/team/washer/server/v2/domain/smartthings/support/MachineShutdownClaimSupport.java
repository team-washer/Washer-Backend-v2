package team.washer.server.v2.domain.smartthings.support;

import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import team.washer.server.v2.domain.machine.entity.Machine;
import team.washer.server.v2.domain.machine.repository.MachineRepository;
import team.washer.server.v2.domain.reservation.repository.ReservationRepository;

/**
 * SmartThings 외부 전원 차단 작업과 예약 생성을 직렬화하는 영속 claim을 관리합니다.
 *
 * <p>
 * claim을 획득·해제하는 트랜잭션은 기기 행과 예약 상태만 다루며, 외부 HTTP 호출은 호출자에게 반환된 뒤 수행합니다.
 */
@Component
@RequiredArgsConstructor
public class MachineShutdownClaimSupport {

    private final MachineRepository machineRepository;
    private final ReservationRepository reservationRepository;

    public record ShutdownClaim(Long machineId, String token, boolean hasUnresolvedCommand) {

        public ShutdownClaim(final Long machineId, final String token) {
            this(machineId, token, false);
        }
    }

    /**
     * 유휴 기기의 전원 차단 claim을 획득합니다. 조회 시점의 후보 목록이 오래되었더라도 기기 락 아래에서 재검증합니다.
     *
     * @param machineId
     *            대상 기기 ID
     * @return 획득한 claim. 신규 활성 예약이나 다른 유효 claim이 있으면 빈 값
     */
    @Transactional
    public Optional<ShutdownClaim> claimIdleMachine(final Long machineId) {
        final var machine = machineRepository.findByIdForUpdate(machineId).orElse(null);
        if (machine == null || machine.isCleaning()) {
            return Optional.empty();
        }

        if (reservationRepository.existsCurrentlyActiveByMachine(machine)) {
            return Optional.empty();
        }

        final var claim = claimLockedMachine(machine);
        if (claim.isPresent()) {
            return claim;
        }
        if (machine.reclaimShutdownCommand()) {
            return Optional.of(new ShutdownClaim(machine.getId(), machine.getShutdownClaimToken(), true));
        }
        return Optional.empty();
    }

    /**
     * 관리자 강제 정지 명령 전에 기기를 보호합니다.
     *
     * <p>
     * 강제 정지는 활성 예약을 취소하는 작업이므로, 유휴 기기 전용 claim과 달리 활성 예약의 존재 여부를 확인하지 않습니다. 명령 결과가
     * 확정되기 전에는 신규 예약이 들어오지 않도록 기기 행을 잠근 상태에서 claim을 획득합니다.
     *
     * @param machineId
     *            대상 기기 ID
     * @return 획득한 claim. 이미 다른 종료 작업이 진행 중이면 비어 있음
     */
    @Transactional
    public Optional<ShutdownClaim> claimForForceStop(final Long machineId) {
        return machineRepository.findByIdForUpdate(machineId).flatMap(this::claimLockedMachine);
    }

    /**
     * 이미 기기 행 락을 보유한 트랜잭션에서 완료 후 전원 차단 claim을 획득합니다.
     *
     * @param machine
     *            락이 획득된 기기
     * @return 획득한 세대 토큰
     */
    public Optional<ShutdownClaim> claimLockedMachine(final Machine machine) {
        return machine.claimShutdown().map(token -> new ShutdownClaim(machine.getId(), token));
    }

    /** 외부 명령 직전에 선점 토큰이 아직 유효한지 짧게 확인합니다. */
    @Transactional(readOnly = true)
    public boolean isActive(final ShutdownClaim claim) {
        return machineRepository.findById(claim.machineId())
                .map(machine -> machine.ownsActiveShutdownClaim(claim.token())).orElse(false);
    }

    /** 외부 전원 차단 명령 직전에 claim을 명령 진행 중 상태로 전환한다. */
    @Transactional
    public boolean beginCommand(final ShutdownClaim claim) {
        return machineRepository.findByIdForUpdate(claim.machineId())
                .map(machine -> machine.beginShutdownCommand(claim.token())).orElse(false);
    }

    /** 외부 호출이 끝난 뒤 같은 세대의 claim만 해제합니다. */
    @Transactional
    public void release(final ShutdownClaim claim) {
        machineRepository.findByIdForUpdate(claim.machineId()).ifPresent(machine -> {
            machine.releaseShutdown(claim.token());
        });
    }
}
