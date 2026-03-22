package team.washer.server.v2.global.util;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import lombok.extern.slf4j.Slf4j;
import team.washer.server.v2.domain.smartthings.service.QueryDeviceStatusService;

@Slf4j
public final class DateTimeUtil {

    private DateTimeUtil() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    public static LocalDateTime parseAndConvertToKoreaTime(String timeStr) {
        try {
            var utcTime = ZonedDateTime.parse(timeStr);
            return utcTime.withZoneSameInstant(ZoneId.of("Asia/Seoul")).toLocalDateTime();
        } catch (Exception e) {
            log.warn("Failed to parse time: {}", timeStr, e);
            return LocalDateTime.now();
        }
    }

    public static LocalDateTime getExpectedCompletionTime(QueryDeviceStatusService queryDeviceStatusService,
            String deviceId) {
        try {
            var status = queryDeviceStatusService.execute(deviceId);
            var completionTimeStr = status.getCompletionTime();

            if (completionTimeStr != null && !completionTimeStr.isBlank()) {
                return parseAndConvertToKoreaTime(completionTimeStr);
            }
        } catch (Exception e) {
            log.warn("Failed to get expected completion time for device: {}", deviceId, e);
        }
        return LocalDateTime.now().plusHours(2);
    }
}
