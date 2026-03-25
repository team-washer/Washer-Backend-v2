package team.washer.server.v2.domain.smartthings.service.impl;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import team.washer.server.v2.domain.smartthings.service.DetectMachinePausedService;
import team.washer.server.v2.domain.smartthings.service.QueryDeviceStatusService;

@Service
@RequiredArgsConstructor
@Slf4j
public class DetectMachinePausedServiceImpl implements DetectMachinePausedService {

    private final QueryDeviceStatusService queryDeviceStatusService;

    @Override
    @Transactional(readOnly = true)
    public boolean execute(String deviceId) {
        try {
            var status = queryDeviceStatusService.execute(deviceId);

            var washerMachineState = status.getWasherOperatingState();
            if ("pause".equalsIgnoreCase(washerMachineState)) {
                log.debug("기기 {} 세탁기가 일시정지 상태 (machineState=pause)", deviceId);
                return true;
            }

            var dryerMachineState = status.getDryerOperatingState();
            if ("pause".equalsIgnoreCase(dryerMachineState)) {
                log.debug("기기 {} 건조기가 일시정지 상태 (machineState=pause)", deviceId);
                return true;
            }

            return false;
        } catch (Exception e) {
            log.warn("기기 {} 일시정지 상태 감지 실패", deviceId, e);
            return false;
        }
    }
}
