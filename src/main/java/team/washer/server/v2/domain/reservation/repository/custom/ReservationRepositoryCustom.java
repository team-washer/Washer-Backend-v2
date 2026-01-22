package team.washer.server.v2.domain.reservation.repository.custom;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import team.washer.server.v2.domain.machine.enums.MachineType;
import team.washer.server.v2.domain.reservation.entity.Reservation;
import team.washer.server.v2.domain.reservation.enums.ReservationStatus;

public interface ReservationRepositoryCustom {

    Page<Reservation> findReservationHistory(Long userId,
            ReservationStatus status,
            LocalDateTime startDate,
            LocalDateTime endDate,
            MachineType machineType,
            Pageable pageable);

    boolean existsConflictingReservation(Long machineId,
            LocalDateTime startTime,
            LocalDateTime endTime,
            Long excludeReservationId);

    List<Reservation> findExpiredReservations(ReservationStatus status,
            LocalDateTime threshold,
            LocalDateTime recentCutoff);
}
