package team.washer.server.v2.domain.reservation.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import team.washer.server.v2.domain.reservation.entity.ReservationCycleLog;
import team.washer.server.v2.domain.reservation.enums.CycleAction;

@Repository
public interface ReservationCycleLogRepository extends JpaRepository<ReservationCycleLog, Long> {

    @Query("SELECT r FROM ReservationCycleLog r ORDER BY r.createdAt DESC")
    List<ReservationCycleLog> findAllOrderByCreatedAtDesc();

    @Query("SELECT r FROM ReservationCycleLog r WHERE r.action = :action ORDER BY r.createdAt DESC")
    List<ReservationCycleLog> findByActionOrderByCreatedAtDesc(CycleAction action);

    @Query("SELECT r FROM ReservationCycleLog r ORDER BY r.createdAt DESC LIMIT 1")
    ReservationCycleLog findLatest();
}
