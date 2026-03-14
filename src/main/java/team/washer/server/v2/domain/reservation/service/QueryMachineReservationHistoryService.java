package team.washer.server.v2.domain.reservation.service;

import java.time.LocalDateTime;

import org.springframework.data.domain.Pageable;

import team.washer.server.v2.domain.reservation.dto.response.MachineReservationHistoryPageResDto;
import team.washer.server.v2.domain.reservation.enums.ReservationStatus;

public interface QueryMachineReservationHistoryService {
    MachineReservationHistoryPageResDto execute(Long machineId,
            ReservationStatus status,
            LocalDateTime startDate,
            LocalDateTime endDate,
            Pageable pageable);
}
