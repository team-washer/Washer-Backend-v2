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

    @Query("SELECT r FROM Reservation r WHERE r.user = :user AND r.status IN :statuses")
    List<Reservation> findByUserAndStatusIn(@Param("user") User user,
            @Param("statuses") List<ReservationStatus> statuses);

    @Query("SELECT r FROM Reservation r WHERE r.machine = :machine AND r.status IN :statuses")
    List<Reservation> findByMachineAndStatusIn(@Param("machine") Machine machine,
            @Param("statuses") List<ReservationStatus> statuses);

    @Query("SELECT r FROM Reservation r WHERE r.status IN ('RESERVED', 'CONFIRMED', 'RUNNING')")
    List<Reservation> findAllActiveReservations();

    @Query("SELECT r FROM Reservation r WHERE r.status = 'RESERVED' AND r.startTime < :threshold")
    List<Reservation> findExpiredReservedReservations(@Param("threshold") LocalDateTime threshold);

    @Query("SELECT r FROM Reservation r WHERE r.status = 'CONFIRMED' AND r.confirmedAt < :threshold")
    List<Reservation> findExpiredConfirmedReservations(@Param("threshold") LocalDateTime threshold);

    @Query("SELECT COUNT(r) FROM Reservation r WHERE r.machine = :machine AND r.status IN ('RESERVED', 'CONFIRMED', 'RUNNING')")
    long countActiveReservationsByMachine(@Param("machine") Machine machine);

    @Query("SELECT r FROM Reservation r WHERE r.user = :user ORDER BY r.createdAt DESC")
    List<Reservation> findReservationHistoryByUser(@Param("user") User user);

    @Query("SELECT r FROM Reservation r WHERE r.status = 'RUNNING'")
    List<Reservation> findAllRunningReservations();

    boolean existsByMachineAndStatusIn(Machine machine, List<ReservationStatus> statuses);
}
