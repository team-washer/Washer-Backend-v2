package team.washer.server.v2.domain.reservation.service;

import team.washer.server.v2.domain.reservation.dto.request.AdminCreateReservationReqDto;
import team.washer.server.v2.domain.reservation.dto.response.AdminReservationResDto;

public interface AdminCreateReservationService {
    AdminReservationResDto execute(AdminCreateReservationReqDto reqDto);
}
