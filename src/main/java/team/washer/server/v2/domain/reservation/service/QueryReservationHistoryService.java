package team.washer.server.v2.domain.reservation.service;

import java.time.LocalDateTime;

import org.springframework.data.domain.Pageable;

import team.washer.server.v2.domain.machine.enums.MachineType;
import team.washer.server.v2.domain.reservation.dto.response.ReservationHistoryPageResDto;
import team.washer.server.v2.domain.reservation.enums.ReservationStatus;

public interface QueryReservationHistoryService {
    ReservationHistoryPageResDto execute(Long userId,
            ReservationStatus status,
            LocalDateTime startDate,
            LocalDateTime endDate,
            MachineType machineType,
            Pageable pageable);
}
