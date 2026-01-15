package team.washer.server.v2.domain.reservation.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import team.washer.server.v2.domain.machine.entity.Machine;
import team.washer.server.v2.domain.reservation.entity.Reservation;
import team.washer.server.v2.domain.reservation.enums.ReservationStatus;
import team.washer.server.v2.domain.user.entity.User;

@Repository
public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    List<Reservation> findByUser(User user);

    List<Reservation> findByMachine(Machine machine);

    List<Reservation> findByStatus(ReservationStatus status);

    List<Reservation> findByStatusIn(List<ReservationStatus> statuses);

    @Query("SELECT r FROM Reservation r WHERE r.user = :user AND r.status IN :statuses")
    List<Reservation> findByUserAndStatusIn(@Param("user") User user,
            @Param("statuses") List<ReservationStatus> statuses);

    @Query("SELECT r FROM Reservation r WHERE r.machine = :machine AND r.status IN :statuses")
    List<Reservation> findByMachineAndStatusIn(@Param("machine") Machine machine,
            @Param("statuses") List<ReservationStatus> statuses);

    default List<Reservation> findAllActiveReservations() {
        return findByStatusIn(
                List.of(ReservationStatus.RESERVED, ReservationStatus.CONFIRMED, ReservationStatus.RUNNING));
    }

    @Query("SELECT r FROM Reservation r WHERE r.status = :status AND r.startTime < :threshold")
    List<Reservation> findExpiredReservedReservations(@Param("status") ReservationStatus status,
            @Param("threshold") LocalDateTime threshold);

    @Query("SELECT r FROM Reservation r WHERE r.status = :status AND r.confirmedAt < :threshold")
    List<Reservation> findExpiredConfirmedReservations(@Param("status") ReservationStatus status,
            @Param("threshold") LocalDateTime threshold);

    @Query("SELECT COUNT(r) FROM Reservation r WHERE r.machine = :machine AND r.status IN :statuses")
    long countActiveReservationsByMachine(@Param("machine") Machine machine,
            @Param("statuses") List<ReservationStatus> statuses);

    @Query("SELECT r FROM Reservation r WHERE r.user = :user ORDER BY r.createdAt DESC")
    List<Reservation> findReservationHistoryByUser(@Param("user") User user);

    default List<Reservation> findAllRunningReservations() {
        return findByStatus(ReservationStatus.RUNNING);
    }

    boolean existsByMachineAndStatusIn(Machine machine, List<ReservationStatus> statuses);
}
