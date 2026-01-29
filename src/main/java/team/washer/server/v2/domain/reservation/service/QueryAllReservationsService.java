package team.washer.server.v2.domain.reservation.service;

import java.time.LocalDateTime;

import team.washer.server.v2.domain.reservation.dto.response.AdminReservationListResDto;
import team.washer.server.v2.domain.reservation.enums.ReservationStatus;

public interface QueryAllReservationsService {
    AdminReservationListResDto execute(String userName,
            String machineName,
            ReservationStatus status,
            LocalDateTime startDate,
            LocalDateTime endDate);
}
