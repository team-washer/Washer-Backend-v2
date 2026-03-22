package team.washer.server.v2.domain.smartthings.service;

import team.washer.server.v2.domain.smartthings.dto.response.SmartThingsDeviceStatusResDto;

public interface QueryDeviceStatusService {
    SmartThingsDeviceStatusResDto execute(String deviceId);
}
