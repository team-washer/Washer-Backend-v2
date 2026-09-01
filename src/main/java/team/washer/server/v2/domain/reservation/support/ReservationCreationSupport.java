package team.washer.server.v2.domain.reservation.support;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import team.themoment.sdk.exception.ExpectedException;
import team.washer.server.v2.domain.admin.repository.WashingBanRepository;
import team.washer.server.v2.domain.machine.entity.Machine;
import team.washer.server.v2.domain.machine.enums.MachineAvailability;
import team.washer.server.v2.domain.machine.enums.MachineStatus;
import team.washer.server.v2.domain.machine.repository.MachineRepository;
import team.washer.server.v2.domain.reservation.entity.Reservation;
import team.washer.server.v2.domain.reservation.enums.ReservationStatus;
import team.washer.server.v2.domain.reservation.repository.ReservationRepository;
import team.washer.server.v2.domain.user.entity.User;
import team.washer.server.v2.global.util.DateTimeUtil;

/**
 * 예약 생성 경로가 공유하는 불변식 검증과 엔티티 생성을 담당하는 컴포넌트.
 *
 * <p>
 * 사용자 본인 예약({@code CreateReservationServiceImpl})과 관리자 대리 예약
 * ({@code AdminCreateReservationServiceImpl})은 "어떤 상태에서 예약이 성립하는가"라는 불변식은
 * 공유하지만, 정책 검증(시간대 제한·48시간 호실 차단·5분 쿨다운)은 본인 예약 경로에만 적용된다. 따라서 이 컴포넌트는 불변식만 담고
 * 정책 검증은 각 서비스가 직접 수행한다.
 *
 * <p>
 * 호출 순서는 {@code validateRoomConstraints} → {@code lockMachine} →
 * {@code validateMachineAndReservations} → {@code create}를 전제로 한다. 특히 기기 비관적 락이
 * 중복 검증보다 먼저 수행되어야 동일 기기에 대한 동시 예약이 직렬화된다.
 */
@Component
@RequiredArgsConstructor
public class ReservationCreationSupport {

    private static final List<ReservationStatus> ACTIVE_STATUSES = List.of(ReservationStatus.RESERVED,
            ReservationStatus.RUNNING);

    private final ReservationRepository reservationRepository;
    private final MachineRepository machineRepository;
    private final WashingBanRepository washingBanRepository;

    /**
     * 사용자의 호실 관련 제약을 검증합니다. 층 제한, 호실 정보 존재 여부, 호실 세탁 강제 금지를 차례로 확인합니다.
     *
     * @param user
     *            예약 주체가 될 사용자
     * @return 검증을 통과한 사용자의 호실 번호
     */
    public String validateRoomConstraints(final User user) {
        user.validateFloorRestriction();

        final String roomNumber = user.getRoomNumber();
        if (roomNumber == null) {
            throw new ExpectedException("호실 정보가 존재하지 않습니다.", HttpStatus.BAD_REQUEST);
        }

        // 호실 세탁 강제 금지 검증
        if (washingBanRepository.existsByRoomNumber(roomNumber)) {
            throw new ExpectedException("해당 호실은 현재 세탁이 금지된 상태입니다.", HttpStatus.FORBIDDEN);
        }

        return roomNumber;
    }

    /**
     * 동일 기기 동시 예약을 직렬화하기 위해 기기를 비관적 쓰기 락으로 조회합니다.
     *
     * @param machineId
     *            조회할 기기 ID
     * @return 락을 획득한 기기
     */
    public Machine lockMachine(final Long machineId) {
        return machineRepository.findByIdForUpdate(machineId)
                .orElseThrow(() -> new ExpectedException("기기를 찾을 수 없습니다", HttpStatus.NOT_FOUND));
    }

    /**
     * 기기 가용성과 중복 예약 불변식을 검증합니다. 기기 가용성 → 기기 단위 중복 → 1인 1예약 → 호실 동일 유형 중복 순으로 확인합니다.
     *
     * <p>
     * 모든 판정은 타임아웃이 지난 예약을 활성으로 세지 않는다. 스케줄러가 아직 정리하지 못한 만료 예약 때문에 새 예약이 막히는 것을 막기
     * 위함이다.
     *
     * @param user
     *            예약 주체가 될 사용자
     * @param machine
     *            락을 획득한 기기
     */
    public void validateMachineAndReservations(final User user, final Machine machine) {
        final var activeMachineReservations = reservationRepository.findByMachineAndStatusIn(machine, ACTIVE_STATUSES);

        // 기기 가용성 검증
        if (machine.getAvailability() != MachineAvailability.AVAILABLE
                && !canReuseStaleReservedSlot(machine, activeMachineReservations)) {
            throw new ExpectedException(String.format("해당 기기를 사용할 수 없습니다. 기기: %s", machine.getName()),
                    HttpStatus.BAD_REQUEST);
        }

        // 기기 단위 중복 예약 검증 (가용성 플래그 드리프트에 대한 방어 심화)
        if (hasCurrentActiveReservation(activeMachineReservations)) {
            throw new ExpectedException(String.format("해당 기기에 이미 진행 중인 예약이 있습니다. 기기: %s", machine.getName()),
                    HttpStatus.CONFLICT);
        }

        // 개인 중복 예약 검증 (1인 1예약)
        final var userActiveReservations = reservationRepository.findByUserAndStatusIn(user, ACTIVE_STATUSES);
        if (hasCurrentActiveReservation(userActiveReservations)) {
            throw new ExpectedException("이미 활성 예약이 존재합니다. 1인 1예약만 가능합니다.", HttpStatus.BAD_REQUEST);
        }

        // 동일 호실의 동일 유형 기기 중복 예약 검증
        final boolean hasDuplicateTypeReservation = reservationRepository
                .findActiveReservationsByRoomNumber(user.getRoomNumber()).stream()
                .filter(reservation -> reservation.getMachine().getType() == machine.getType())
                .anyMatch(reservation -> reservation.isActive() && !reservation.isExpired());
        if (hasDuplicateTypeReservation) {
            throw new ExpectedException(String.format("해당 호실에 이미 %s 예약이 존재합니다. 동일 유형의 기기는 동시에 두 개 이상 예약할 수 없습니다.",
                    machine.getType().getDescription()), HttpStatus.BAD_REQUEST);
        }
    }

    /**
     * 타임아웃이 지나지 않은 진짜 활성 예약이 있는지 판정합니다.
     *
     * @param reservations
     *            판정 대상 예약 목록
     * @return 만료되지 않은 활성 예약 존재 여부
     */
    private boolean hasCurrentActiveReservation(final List<Reservation> reservations) {
        return reservations.stream().anyMatch(reservation -> reservation.isActive() && !reservation.isExpired());
    }

    /**
     * 만료된 예약만 남아 RESERVED로 굳어버린 기기를 재사용할 수 있는지 판정합니다.
     *
     * @param machine
     *            락을 획득한 기기
     * @param activeReservations
     *            해당 기기의 활성 상태 예약 목록
     * @return 재사용 가능 여부
     */
    private boolean canReuseStaleReservedSlot(final Machine machine, final List<Reservation> activeReservations) {
        return machine.getStatus() == MachineStatus.NORMAL && machine.getAvailability() == MachineAvailability.RESERVED
                && !hasCurrentActiveReservation(activeReservations);
    }

    /**
     * 예약 엔티티를 생성하고 기기를 예약 상태로 전환합니다.
     *
     * @param user
     *            예약 주체
     * @param machine
     *            예약 대상 기기
     * @param createdBy
     *            대리 예약을 생성한 관리자. 사용자 본인 예약이면 {@code null}
     * @return 저장된 예약
     */
    public Reservation create(final User user, final Machine machine, final User createdBy) {
        final var now = DateTimeUtil.nowInKorea();
        final Reservation reservation = Reservation.builder().user(user).machine(machine).createdBy(createdBy)
                .reservedAt(now).dayOfWeek(now.getDayOfWeek()).status(ReservationStatus.RESERVED).build();

        machine.markAsReserved();
        machineRepository.save(machine);

        return reservationRepository.save(reservation);
    }
}
