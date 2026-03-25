package team.washer.server.v2.domain.smartthings.service.impl;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import team.washer.server.v2.domain.smartthings.service.DetectMachineInterruptedService;
import team.washer.server.v2.domain.smartthings.service.QueryDeviceStatusService;

@Service
@RequiredArgsConstructor
@Slf4j
public class DetectMachineInterruptedServiceImpl implements DetectMachineInterruptedService {

    private final QueryDeviceStatusService queryDeviceStatusService;

    @Override
    @Transactional(readOnly = true)
    public boolean execute(String deviceId) {
        try {
            var status = queryDeviceStatusService.execute(deviceId);

            // 전원이 꺼진 경우
            var switchStatus = status.getSwitchStatus();
            if ("off".equalsIgnoreCase(switchStatus)) {
                log.debug("기기 {} 전원이 꺼져 있음 (switch=off)", deviceId);
                return true;
            }

            // 완료 없이 정지된 경우: machineState="stop" 이지만 jobState가 정상 완료값이 아닌 경우
            var washerMachineState = status.getWasherOperatingState();
            var washerJobState = status.getWasherJobState();
            if ("stop".equalsIgnoreCase(washerMachineState) && !"finish".equalsIgnoreCase(washerJobState)) {
                log.debug("기기 {} 세탁기가 비정상 정지됨 (machineState=stop, jobState={})", deviceId, washerJobState);
                return true;
            }

            var dryerMachineState = status.getDryerOperatingState();
            var dryerJobState = status.getDryerJobState();
            if ("stop".equalsIgnoreCase(dryerMachineState) && !"finished".equalsIgnoreCase(dryerJobState)) {
                log.debug("기기 {} 건조기가 비정상 정지됨 (machineState=stop, jobState={})", deviceId, dryerJobState);
                return true;
            }

            return false;
        } catch (Exception e) {
            log.warn("기기 {} 중단 상태 감지 실패", deviceId, e);
            return false;
        }
    }
}
