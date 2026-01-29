package team.washer.server.v2.domain.smartthings.service;

import team.washer.server.v2.domain.smartthings.dto.request.SmartThingsCommandReqDto;

public interface SendDeviceCommandService {
    void execute(String deviceId, SmartThingsCommandReqDto command);
}
