package team.washer.server.v2.domain.smartthings.service.impl;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import team.washer.server.v2.domain.smartthings.dto.response.SmartThingsDeviceStatusResDto;
import team.washer.server.v2.domain.smartthings.service.QueryAllDevicesStatusService;
import team.washer.server.v2.domain.smartthings.service.QueryDeviceStatusService;

@Service
@RequiredArgsConstructor
@Slf4j
public class QueryAllDevicesStatusServiceImpl implements QueryAllDevicesStatusService {

    private final QueryDeviceStatusService queryDeviceStatusService;

    @Override
    @Transactional(readOnly = true)
    public Map<String, SmartThingsDeviceStatusResDto> execute(List<String> deviceIds) {
        if (deviceIds == null || deviceIds.isEmpty()) {
            return Map.of();
        }

        log.info("Querying status for {} devices in parallel", deviceIds.size());

        var futures = deviceIds.stream().map(deviceId -> CompletableFuture.supplyAsync(() -> {
            try {
                var status = queryDeviceStatusService.execute(deviceId);
                return Map.entry(deviceId, status);
            } catch (Exception e) {
                log.warn("Failed to query status for device: {}", deviceId, e);
                return null;
            }
        })).toList();

        var results = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> futures.stream().map(CompletableFuture::join).filter(entry -> entry != null)
                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)))
                .join();

        log.info("Successfully queried status for {}/{} devices", results.size(), deviceIds.size());

        return results;
    }
}
