package team.washer.server.v2.domain.reservation.service;

import team.washer.server.v2.domain.reservation.dto.response.PenaltyStatusResDto;

public interface QueryPenaltyStatusService {
    PenaltyStatusResDto execute(Long userId);
}
