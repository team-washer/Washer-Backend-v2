package team.washer.server.v2.domain.smartthings.service.impl;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import team.washer.server.v2.domain.smartthings.dto.request.SmartThingsCommandReqDto;
import team.washer.server.v2.domain.smartthings.service.SendDeviceCommandService;
import team.washer.server.v2.domain.smartthings.support.SmartThingsTokenProvider;
import team.washer.server.v2.global.common.error.exception.ErrorCodeException;
import team.washer.server.v2.global.thirdparty.smartthings.feign.SmartThingsErrorMapper;
import team.washer.server.v2.global.thirdparty.smartthings.feign.SmartThingsFeignClient;

@Service
@RequiredArgsConstructor
@Slf4j
public class SendDeviceCommandServiceImpl implements SendDeviceCommandService {

    private final SmartThingsFeignClient feignClient;
    private final SmartThingsTokenProvider tokenProvider;

    /**
     * 기기에 명령을 전송한다. 외부 HTTP 호출이 DB 커넥션을 점유하지 않도록 트랜잭션 없이 수행한다.
     */
    @Override
    public void execute(String deviceId, SmartThingsCommandReqDto command) {
        try {
            var authorization = "Bearer " + tokenProvider.getValidAccessToken();
            feignClient.sendDeviceCommand(authorization, deviceId, command);

            log.debug("smartthings command sent successfully deviceId={} command={}", deviceId, command);
        } catch (ErrorCodeException e) {
            throw e;
        } catch (Exception e) {
            final var mapped = SmartThingsErrorMapper.toCommandException(e);
            log.error("smartthings command failed deviceId={} errorCode={}", deviceId, mapped.getErrorCode(), e);
            throw mapped;
        }
    }
}
