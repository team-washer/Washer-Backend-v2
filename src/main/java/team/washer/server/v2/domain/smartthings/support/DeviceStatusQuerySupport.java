package team.washer.server.v2.domain.smartthings.support;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import team.themoment.sdk.exception.ExpectedException;
import team.washer.server.v2.domain.smartthings.dto.response.SmartThingsDeviceStatusResDto;
import team.washer.server.v2.domain.smartthings.repository.SmartThingsTokenRepository;
import team.washer.server.v2.global.thirdparty.smartthings.feign.SmartThingsFeignClient;

/**
 * SmartThings 기기 상태 조회를 담당하는 지원 컴포넌트.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DeviceStatusQuerySupport {

    private final SmartThingsFeignClient feignClient;
    private final SmartThingsTokenRepository tokenRepository;

    /**
     * 단일 기기의 SmartThings 상태를 조회한다.
     */
    @Transactional(readOnly = true)
    public SmartThingsDeviceStatusResDto queryDeviceStatus(String deviceId) {
        try {
            var token = tokenRepository.findSingletonToken()
                    .orElseThrow(() -> new ExpectedException("SmartThings 토큰이 존재하지 않습니다", HttpStatus.NOT_FOUND));

            if (!token.isValid()) {
                throw new ExpectedException("SmartThings 토큰이 만료되었거나 유효하지 않습니다", HttpStatus.NOT_FOUND);
            }

            var authorization = "Bearer " + token.getAccessToken();
            return feignClient.getDeviceStatus(authorization, deviceId);
        } catch (ExpectedException e) {
            log.error("smartthings token not found or invalid", e);
            throw e;
        } catch (Exception e) {
            log.error("smartthings failed to query device status deviceId={}", deviceId, e);
            throw new ExpectedException("기기 상태 조회에 실패했습니다: " + e.getMessage(), HttpStatus.BAD_GATEWAY);
        }
    }

    /**
     * 여러 기기의 SmartThings 상태를 병렬로 조회한다.
     */
    @Transactional(readOnly = true)
    public Map<String, SmartThingsDeviceStatusResDto> queryAllDevicesStatus(List<String> deviceIds) {
        if (deviceIds == null || deviceIds.isEmpty()) {
            return Map.of();
        }

        log.info("Querying status for {} devices in parallel", deviceIds.size());

        var futures = deviceIds.stream().map(deviceId -> CompletableFuture.supplyAsync(() -> {
            try {
                var status = queryDeviceStatus(deviceId);
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
