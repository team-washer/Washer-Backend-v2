package team.washer.server.v2.domain.smartthings.service.impl;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import team.washer.server.v2.domain.smartthings.service.DetectMachineRunningService;
import team.washer.server.v2.domain.smartthings.service.QueryDeviceStatusService;

@Service
@RequiredArgsConstructor
@Slf4j
public class DetectMachineRunningServiceImpl implements DetectMachineRunningService {

    private final QueryDeviceStatusService queryDeviceStatusService;

    @Override
    @Transactional(readOnly = true)
    public boolean execute(String deviceId) {
        try {
            var status = queryDeviceStatusService.execute(deviceId);

            var washerOperatingState = status.getWasherOperatingState();
            var dryerOperatingState = status.getDryerOperatingState();

            var isRunning = "running".equalsIgnoreCase(washerOperatingState)
                    || "running".equalsIgnoreCase(dryerOperatingState);

            if (isRunning) {
                log.debug("Device {} is running", deviceId);
            }

            return isRunning;
        } catch (Exception e) {
            log.warn("Failed to detect running state for device: {}", deviceId, e);
            return false;
        }
    }
}
