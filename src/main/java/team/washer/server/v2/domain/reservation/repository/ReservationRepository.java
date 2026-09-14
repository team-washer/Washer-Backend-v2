package team.washer.server.v2.domain.reservation.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import team.washer.server.v2.domain.machine.entity.Machine;
import team.washer.server.v2.domain.reservation.entity.Reservation;
import team.washer.server.v2.domain.reservation.enums.ReservationStatus;
import team.washer.server.v2.domain.reservation.repository.custom.ReservationRepositoryCustom;
import team.washer.server.v2.domain.reservation.util.ActiveReservationSelector;
import team.washer.server.v2.domain.user.entity.User;

@Repository
public interface ReservationRepository extends JpaRepository<Reservation, Long>, ReservationRepositoryCustom {

    List<Reservation> findByUser(User user);

    List<Reservation> findByMachine(Machine machine);

    List<Reservation> findByStatus(ReservationStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Reservation r JOIN FETCH r.machine JOIN FETCH r.user WHERE r.id = :id")
    Optional<Reservation> findByIdForUpdate(@Param("id") Long id);

    @Query("SELECT r FROM Reservation r JOIN FETCH r.machine JOIN FETCH r.user WHERE r.status = :status")
    List<Reservation> findByStatusWithMachineAndUser(@Param("status") ReservationStatus status);

    List<Reservation> findByStatusIn(List<ReservationStatus> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Reservation r JOIN FETCH r.machine WHERE r.user = :user AND r.status IN :statuses")
    List<Reservation> findByUserAndStatusInForUpdate(@Param("user") User user,
            @Param("statuses") List<ReservationStatus> statuses);

    /**
     * 사용자의 지정 상태 예약이 점유한 기기 ID를 잠금 없이 조회합니다. 예약 락보다 기기 락을 먼저 잡아야 할 때 사용합니다.
     */
    @Query("SELECT DISTINCT r.machine.id FROM Reservation r WHERE r.user = :user AND r.status IN :statuses")
    List<Long> findMachineIdsByUserAndStatusIn(@Param("user") User user,
            @Param("statuses") List<ReservationStatus> statuses);

    /**
     * 기기의 지정 상태 예약을 비관적 쓰기 락으로 조회합니다. 락 대기 이후 커밋된 다른 트랜잭션의 예약까지 반영해 재검증할 때 사용합니다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Reservation r WHERE r.machine = :machine AND r.status IN :statuses")
    List<Reservation> findByMachineAndStatusInForUpdate(@Param("machine") Machine machine,
            @Param("statuses") List<ReservationStatus> statuses);

    default List<Reservation> findAllActiveReservations() {
        return findByStatusIn(List.of(ReservationStatus.RESERVED, ReservationStatus.RUNNING));
    }

    @Query("SELECT r FROM Reservation r WHERE r.machine.id = :machineId AND r.status IN :statuses ORDER BY r.createdAt DESC")
    List<Reservation> findFirstActiveReservationByMachineId(@Param("machineId") Long machineId,
            @Param("statuses") List<ReservationStatus> statuses);

    /**
     * 기기의 대표 활성 예약을 조회한다. 만료 예약 제외는
     * {@link ReservationRepositoryCustom#findCurrentlyActiveByMachineId(Long)}가 쿼리
     * 단계에서 처리하고, 남은 후보 중 대표를 고르는 규칙은 {@link ActiveReservationSelector}가 정의한다.
     *
     * <p>
     * 타임아웃이 지난 RESERVED 예약만 남은 기기는 {@link Optional#empty()}가 된다. 스케줄러가 아직 정리하지 못한
     * 만료 예약이 기기를 점유한 것처럼 보이게 하지 않기 위함이다.
     */
    default Optional<Reservation> findCurrentlyActiveReservationByMachineId(Long machineId) {
        return ActiveReservationSelector.selectPrimary(findCurrentlyActiveByMachineId(machineId));
    }

    @Query("SELECT COUNT(r) FROM Reservation r WHERE r.machine = :machine AND r.status IN :statuses")
    long countActiveReservationsByMachine(@Param("machine") Machine machine,
            @Param("statuses") List<ReservationStatus> statuses);

    @Query("SELECT r FROM Reservation r WHERE r.user = :user ORDER BY r.createdAt DESC")
    List<Reservation> findReservationHistoryByUser(@Param("user") User user);

    default List<Reservation> findAllRunningReservations() {
        return findByStatus(ReservationStatus.RUNNING);
    }

    @Query("SELECT DISTINCT r.machine.id FROM Reservation r WHERE r.status IN :statuses")
    List<Long> findMachineIdsByStatusIn(@Param("statuses") List<ReservationStatus> statuses);
}
