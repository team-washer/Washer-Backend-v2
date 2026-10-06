package team.washer.server.v2.domain.smartthings.support;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import team.washer.server.v2.domain.smartthings.dto.response.SmartThingsDeviceStatusResDto;
import team.washer.server.v2.global.common.error.code.ErrorCode;
import team.washer.server.v2.global.common.error.exception.ErrorCodeException;
import team.washer.server.v2.global.thirdparty.smartthings.feign.SmartThingsErrorMapper;
import team.washer.server.v2.global.thirdparty.smartthings.feign.SmartThingsFeignClient;

/**
 * SmartThings 기기 상태 조회를 담당하는 지원 컴포넌트.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DeviceStatusQuerySupport {

    private final SmartThingsFeignClient feignClient;
    private final SmartThingsTokenProvider tokenProvider;

    /**
     * 단일 기기의 SmartThings 상태를 조회한다. 외부 HTTP 호출이 DB 커넥션을 점유하지 않도록 트랜잭션 없이 수행한다.
     */
    public SmartThingsDeviceStatusResDto queryDeviceStatus(String deviceId) {
        String accessToken = null;
        try {
            accessToken = tokenProvider.getValidAccessToken();
            var authorization = "Bearer " + accessToken;
            final var response = feignClient.getDeviceStatus(authorization, deviceId);
            if (response == null || response.components() == null || response.components().get("main") == null) {
                throw new ErrorCodeException(ErrorCode.SMARTTHINGS_RESPONSE_INVALID);
            }
            return response;
        } catch (ErrorCodeException e) {
            if (e.getErrorCode() == ErrorCode.SMARTTHINGS_TOKEN_INVALID) {
                invalidateRejectedToken(accessToken);
            }
            log.warn("smartthings status query rejected errorCode={} deviceId={}", e.getErrorCode(), deviceId);
            throw e;
        } catch (Exception e) {
            final var mapped = SmartThingsErrorMapper.toStatusException(e);
            log.error("smartthings status query failed deviceId={} errorCode={}", deviceId, mapped.getErrorCode());
            if (mapped.getErrorCode() == ErrorCode.SMARTTHINGS_TOKEN_INVALID) {
                invalidateRejectedToken(accessToken);
            }
            throw mapped;
        }
    }

    /**
     * 여러 기기의 SmartThings 상태를 병렬로 조회한다.
     */
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
                var errorCode = e instanceof ErrorCodeException errorCodeException
                        ? errorCodeException.getErrorCode()
                        : ErrorCode.SMARTTHINGS_STATUS_UNAVAILABLE;
                log.warn("smartthings bulk status query skipped deviceId={} errorCode={}", deviceId, errorCode, e);
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

    private void invalidateRejectedToken(final String accessToken) {
        try {
            tokenProvider.invalidate(accessToken);
        } catch (Exception e) {
            log.error("smartthings rejected token invalidation failed", e);
        }
    }
}
