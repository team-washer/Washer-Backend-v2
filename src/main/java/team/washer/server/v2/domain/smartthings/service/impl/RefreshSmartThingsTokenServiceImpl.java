package team.washer.server.v2.domain.smartthings.service.impl;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import team.themoment.sdk.exception.ExpectedException;
import team.washer.server.v2.domain.smartthings.dto.response.SmartThingsTokenExchangeResDto;
import team.washer.server.v2.domain.smartthings.entity.SmartThingsToken;
import team.washer.server.v2.domain.smartthings.repository.SmartThingsTokenRepository;
import team.washer.server.v2.domain.smartthings.service.RefreshSmartThingsTokenService;
import team.washer.server.v2.global.thirdparty.smartthings.config.SmartThingsEnvironment;
import team.washer.server.v2.global.thirdparty.smartthings.feign.SmartThingsOAuthClient;

@Service
@RequiredArgsConstructor
@Slf4j
public class RefreshSmartThingsTokenServiceImpl implements RefreshSmartThingsTokenService {

    private static final String GRANT_TYPE_REFRESH_TOKEN = "refresh_token";

    private final SmartThingsOAuthClient smartThingsOAuthClient;
    private final SmartThingsTokenRepository smartThingsTokenRepository;
    private final SmartThingsEnvironment smartThingsEnvironment;

    @Override
    @Transactional
    public void execute() {
        try {
            log.info("Refreshing SmartThings token");

            var token = smartThingsTokenRepository.findSingletonTokenWithLock()
                    .orElseThrow(() -> new ExpectedException("SmartThings 토큰이 존재하지 않습니다", HttpStatus.NOT_FOUND));

            if (!token.isExpiredOrExpiringSoon()) {
                log.debug("SmartThings token is still valid, skipping refresh");
                return;
            }

            var basicAuth = "Basic " + Base64.getEncoder().encodeToString(
                    (smartThingsEnvironment.clientId() + ":" + smartThingsEnvironment.clientSecret())
                            .getBytes(StandardCharsets.UTF_8));
            var formBody = "grant_type=" + GRANT_TYPE_REFRESH_TOKEN
                    + "&refresh_token=" + token.getRefreshToken();

            var response = smartThingsOAuthClient.refreshToken(basicAuth, formBody);

            updateToken(token, response);

            log.info("Successfully refreshed SmartThings token");
        } catch (ExpectedException e) {
            log.warn("SmartThings token not found, skipping refresh");
            throw e;
        } catch (Exception e) {
            log.error("Failed to refresh SmartThings token", e);
            throw new ExpectedException("SmartThings 토큰 갱신에 실패했습니다: " + e.getMessage(),
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private void updateToken(SmartThingsToken token, SmartThingsTokenExchangeResDto response) {
        var expiresAt = LocalDateTime.now().plusSeconds(response.expiresIn());
        token.updateTokens(response.accessToken(), response.refreshToken(), expiresAt);
        smartThingsTokenRepository.save(token);
    }
}
