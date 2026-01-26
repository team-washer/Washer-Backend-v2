package team.washer.server.v2.domain.smartthings.service.impl;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import team.washer.server.v2.domain.smartthings.dto.response.SmartThingsTokenExchangeResDto;
import team.washer.server.v2.domain.smartthings.entity.SmartThingsToken;
import team.washer.server.v2.domain.smartthings.exception.SmartThingsTokenNotFoundException;
import team.washer.server.v2.domain.smartthings.exception.SmartThingsTokenRefreshException;
import team.washer.server.v2.domain.smartthings.repository.SmartThingsTokenRepository;
import team.washer.server.v2.domain.smartthings.service.RefreshSmartThingsTokenService;
import team.washer.server.v2.global.thirdparty.smartthings.config.SmartThingsConfig;
import team.washer.server.v2.global.thirdparty.smartthings.feign.SmartThingsOAuthClient;

@Service
@RequiredArgsConstructor
@Slf4j
public class RefreshSmartThingsTokenServiceImpl implements RefreshSmartThingsTokenService {

    private static final String GRANT_TYPE_REFRESH_TOKEN = "refresh_token";

    private final SmartThingsOAuthClient smartThingsOAuthClient;
    private final SmartThingsTokenRepository smartThingsTokenRepository;
    private final SmartThingsConfig smartThingsConfig;

    @Override
    @Transactional
    public void execute() {
        try {
            log.info("Refreshing SmartThings token");

            var token = smartThingsTokenRepository.findSingletonTokenWithLock()
                    .orElseThrow(SmartThingsTokenNotFoundException::new);

            if (!token.isExpiredOrExpiringSoon()) {
                log.debug("SmartThings token is still valid, skipping refresh");
                return;
            }

            var response = smartThingsOAuthClient.refreshToken(GRANT_TYPE_REFRESH_TOKEN,
                    smartThingsConfig.getClientId(),
                    smartThingsConfig.getClientSecret(),
                    token.getRefreshToken());

            updateToken(token, response);

            log.info("Successfully refreshed SmartThings token");
        } catch (SmartThingsTokenNotFoundException e) {
            log.warn("SmartThings token not found, skipping refresh");
            throw e;
        } catch (Exception e) {
            log.error("Failed to refresh SmartThings token", e);
            throw new SmartThingsTokenRefreshException("SmartThings 토큰 갱신에 실패했습니다: " + e.getMessage(), e);
        }
    }

    private void updateToken(SmartThingsToken token, SmartThingsTokenExchangeResDto response) {
        var expiresAt = LocalDateTime.now().plusSeconds(response.expiresIn());
        token.updateTokens(response.accessToken(), response.refreshToken(), expiresAt);
        smartThingsTokenRepository.save(token);
    }
}
