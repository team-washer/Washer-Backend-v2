package team.washer.server.v2.domain.smartthings.service.impl;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import team.washer.server.v2.domain.smartthings.dto.response.SmartThingsDeviceStatusResDto;
import team.washer.server.v2.domain.smartthings.exception.SmartThingsApiException;
import team.washer.server.v2.domain.smartthings.exception.SmartThingsTokenNotFoundException;
import team.washer.server.v2.domain.smartthings.repository.SmartThingsTokenRepository;
import team.washer.server.v2.domain.smartthings.service.QueryDeviceStatusService;
import team.washer.server.v2.global.thirdparty.smartthings.feign.SmartThingsFeignClient;

@Service
@RequiredArgsConstructor
@Slf4j
public class QueryDeviceStatusServiceImpl implements QueryDeviceStatusService {

    private final SmartThingsFeignClient feignClient;
    private final SmartThingsTokenRepository tokenRepository;

    @Override
    @Transactional(readOnly = true)
    public SmartThingsDeviceStatusResDto execute(String deviceId) {
        try {
            var token = tokenRepository.findSingletonToken().orElseThrow(SmartThingsTokenNotFoundException::new);

            if (!token.isValid()) {
                throw new SmartThingsTokenNotFoundException("SmartThings 토큰이 만료되었거나 유효하지 않습니다");
            }

            var authorization = "Bearer " + token.getAccessToken();
            return feignClient.getDeviceStatus(authorization, deviceId);
        } catch (SmartThingsTokenNotFoundException e) {
            log.error("SmartThings token not found or invalid", e);
            throw e;
        } catch (Exception e) {
            log.error("Failed to query device status for deviceId: {}", deviceId, e);
            throw new SmartThingsApiException("기기 상태 조회에 실패했습니다: " + e.getMessage());
        }
    }
}
