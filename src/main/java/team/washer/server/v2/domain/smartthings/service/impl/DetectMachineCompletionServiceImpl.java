package team.washer.server.v2.domain.smartthings.service.impl;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import team.washer.server.v2.domain.smartthings.service.DetectMachineCompletionService;
import team.washer.server.v2.domain.smartthings.service.QueryDeviceStatusService;

@Service
@RequiredArgsConstructor
@Slf4j
public class DetectMachineCompletionServiceImpl implements DetectMachineCompletionService {

    private final QueryDeviceStatusService queryDeviceStatusService;

    @Override
    @Transactional(readOnly = true)
    public Optional<LocalDateTime> execute(String deviceId) {
        try {
            var status = queryDeviceStatusService.execute(deviceId);

            var washerJobState = status.getWasherJobState();
            var dryerJobState = status.getDryerJobState();

            var isFinished = "finished".equalsIgnoreCase(washerJobState) || "finished".equalsIgnoreCase(dryerJobState);

            if (isFinished) {
                log.debug("Device {} job is finished", deviceId);
                return Optional.of(LocalDateTime.now());
            }

            return Optional.empty();
        } catch (Exception e) {
            log.warn("Failed to detect completion state for device: {}", deviceId, e);
            return Optional.empty();
        }
    }

    private LocalDateTime parseAndConvertToKoreaTime(String timeStr) {
        try {
            var utcTime = ZonedDateTime.parse(timeStr);
            return utcTime.withZoneSameInstant(ZoneId.of("Asia/Seoul")).toLocalDateTime();
        } catch (Exception e) {
            log.warn("Failed to parse time: {}", timeStr, e);
            return LocalDateTime.now();
        }
    }
}
