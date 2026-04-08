package team.washer.server.v2.domain.reservation.service;

import team.washer.server.v2.domain.reservation.dto.response.AdminMachineHistoryResDto;

public interface QueryAdminMachineHistoryService {
    AdminMachineHistoryResDto execute(String machineName);
}