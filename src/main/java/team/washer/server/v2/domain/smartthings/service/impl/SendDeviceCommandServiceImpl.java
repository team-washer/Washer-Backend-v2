package team.washer.server.v2.domain.smartthings.service.impl;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import team.washer.server.v2.domain.smartthings.dto.request.SmartThingsCommandReqDto;
import team.washer.server.v2.domain.smartthings.repository.SmartThingsTokenRepository;
import team.washer.server.v2.domain.smartthings.service.SendDeviceCommandService;
import team.washer.server.v2.global.common.error.exception.ExpectedException;
import team.washer.server.v2.global.thirdparty.smartthings.feign.SmartThingsFeignClient;

@Service
@RequiredArgsConstructor
@Slf4j
public class SendDeviceCommandServiceImpl implements SendDeviceCommandService {

    private final SmartThingsFeignClient feignClient;
    private final SmartThingsTokenRepository tokenRepository;

    @Override
    @Transactional(readOnly = true)
    public void execute(String deviceId, SmartThingsCommandReqDto command) {
        try {
            var token = tokenRepository.findSingletonToken()
                    .orElseThrow(() -> new ExpectedException("SmartThings 토큰이 존재하지 않습니다", HttpStatus.NOT_FOUND));

            if (!token.isValid()) {
                throw new ExpectedException("SmartThings 토큰이 만료되었거나 유효하지 않습니다", HttpStatus.NOT_FOUND);
            }

            var authorization = "Bearer " + token.getAccessToken();
            feignClient.sendDeviceCommand(authorization, deviceId, command);

            log.info("Successfully sent command to device: {}", deviceId);
        } catch (ExpectedException e) {
            log.error("SmartThings token not found or invalid", e);
            throw e;
        } catch (Exception e) {
            log.error("Failed to send command to device: {}", deviceId, e);
            throw new ExpectedException("기기 명령 전송에 실패했습니다: " + e.getMessage(), HttpStatus.BAD_GATEWAY);
        }
    }
}
