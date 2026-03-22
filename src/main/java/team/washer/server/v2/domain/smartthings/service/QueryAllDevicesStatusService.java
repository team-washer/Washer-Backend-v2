package team.washer.server.v2.domain.smartthings.service;

import java.util.List;
import java.util.Map;

import team.washer.server.v2.domain.smartthings.dto.response.SmartThingsDeviceStatusResDto;

public interface QueryAllDevicesStatusService {
    Map<String, SmartThingsDeviceStatusResDto> execute(List<String> deviceIds);
}
