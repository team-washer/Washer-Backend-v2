package team.washer.server.v2.domain.reservation.support;

import java.util.LinkedHashSet;
import java.util.List;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import team.washer.server.v2.domain.machine.entity.Machine;
import team.washer.server.v2.domain.machine.repository.MachineRepository;
import team.washer.server.v2.domain.reservation.entity.Reservation;
import team.washer.server.v2.domain.reservation.enums.ReservationStatus;
import team.washer.server.v2.domain.reservation.repository.ReservationRepository;
import team.washer.server.v2.domain.user.entity.User;

/**
 * 사용자 삭제 경로가 공유하는 예약 정리와 기기 해제를 담당하는 컴포넌트.
 *
 * <p>
 * 본인 탈퇴({@code WithdrawUserServiceImpl})와 관리자
 * 삭제({@code DeleteUserServiceImpl})는 "사용자를 지우기 전에 그가 붙잡고 있던 기기를 놓아준다"는 정리 동작을
 * 공유한다. 반면 삭제 허용 여부를 판정하는 기준은 서로 다르다. 본인 탈퇴는 {@code RUNNING} 예약을
 * {@code CONFLICT}로 막고, 관리자 삭제는 만료되지 않은 활성 예약 전체를 {@code BAD_REQUEST}로 막는다. 따라서
 * 이 컴포넌트는 정리 동작만 담고 차단 판정은 각 서비스가 직접 수행한다.
 *
 * <p>
 * {@code User.reservations}가 {@code cascade = ALL, orphanRemoval = true}이므로
 * 사용자를 삭제하면 예약 행은 함께 사라진다. 그러나 기기의 {@code availability}는 예약과 별개의 행이라 함께 되돌아가지
 * 않는다. 정리 없이 삭제하면 기기가 {@code RESERVED}로 굳은 채 남아 통세척 대상에서 제외되므로, 삭제 전에 반드시
 * {@code cancelAndReleaseMachines}를 호출해야 한다.
 *
 * <p>
 * 락 순서는 예약 생성과 같은 사용자 → 기기 → 예약이다. 호출 측은 사용자 행을 먼저 비관적 락으로 조회한 뒤
 * {@code lockActiveReservations}를 호출해야 한다.
 */
@Component
@RequiredArgsConstructor
public class UserReservationCleanupSupport {

    private static final List<ReservationStatus> ACTIVE_STATUSES = List.of(ReservationStatus.RESERVED,
            ReservationStatus.RUNNING);

    private final ReservationRepository reservationRepository;
    private final MachineRepository machineRepository;

    /**
     * 사용자에게 남아 있는 활성 상태 예약이 점유한 기기를 먼저 잠근 뒤, 예약을 기기와 함께 잠금 조회합니다. 만료 여부와 무관하게
     * {@code RESERVED}, {@code RUNNING} 상태의 예약을 모두 가져옵니다. 라이프사이클 스케줄러가 아직 정리하지 못한
     * 만료 예약도 기기를 붙잡고 있으므로 정리 대상에 포함되어야 하기 때문입니다.
     *
     * <p>
     * 예약 생성({@code ReservationCreationSupport.lockMachine})과 같은 기기 락으로 직렬화하여, 정리 도중
     * 같은 기기에 새 예약이 생기는 경합을 막습니다. 교착을 피하기 위해 기기는 ID 오름차순으로 잠급니다.
     *
     * @param user
     *            비관적 락으로 조회한 정리 대상 사용자
     * @return 활성 상태 예약 목록(기기 페치 완료)
     */
    public List<Reservation> lockActiveReservations(final User user) {
        reservationRepository.findMachineIdsByUserAndStatusIn(user, ACTIVE_STATUSES).stream().sorted()
                .forEach(machineRepository::findByIdForUpdate);
        return reservationRepository.findByUserAndStatusInForUpdate(user, ACTIVE_STATUSES);
    }

    /**
     * 전달받은 예약을 모두 취소하고, 그 예약이 점유하던 기기를 해제합니다. 기기 해제는
     * {@link Machine#releaseIfHeld()}에 위임하므로 고장으로 내려둔 기기는 {@code UNAVAILABLE}로
     * 유지됩니다.
     *
     * <p>
     * 기기 락을 기다리는 동안 다른 사용자의 예약이 같은 기기에 커밋되었을 수 있으므로, 해제 직전에 기기의 활성 예약을 잠금 조회로 다시
     * 확인하고 다른 활성 예약이 있으면 기기를 해제하지 않습니다.
     *
     * @param reservations
     *            {@link #lockActiveReservations(User)}로 잠근 정리 대상 예약 목록
     */
    public void cancelAndReleaseMachines(final List<Reservation> reservations) {
        final var machines = new LinkedHashSet<Machine>();
        for (final var reservation : reservations) {
            reservation.cancel();
            machines.add(reservation.getMachine());
        }

        for (final var machine : machines) {
            if (!isHeldByOtherReservation(machine, reservations)) {
                machine.releaseIfHeld();
            }
        }
        machineRepository.saveAll(machines);
    }

    // 같은 영속성 컨텍스트에서 조회한 예약은 동일 인스턴스이므로 정리 대상 여부를 인스턴스로 비교한다
    private boolean isHeldByOtherReservation(final Machine machine, final List<Reservation> cancelledReservations) {
        return reservationRepository.findByMachineAndStatusInForUpdate(machine, ACTIVE_STATUSES).stream().anyMatch(
                reservation -> !cancelledReservations.contains(reservation) && reservation.isCurrentlyActive());
    }
}
