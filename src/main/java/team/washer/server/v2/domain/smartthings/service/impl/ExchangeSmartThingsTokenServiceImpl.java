package team.washer.server.v2.domain.smartthings.service.impl;

import java.net.URLEncoder;
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
import team.washer.server.v2.domain.smartthings.service.ExchangeSmartThingsTokenService;
import team.washer.server.v2.global.thirdparty.smartthings.config.SmartThingsEnvironment;
import team.washer.server.v2.global.thirdparty.smartthings.feign.SmartThingsOAuthClient;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExchangeSmartThingsTokenServiceImpl implements ExchangeSmartThingsTokenService {

    private static final String GRANT_TYPE_AUTHORIZATION_CODE = "authorization_code";

    private final SmartThingsOAuthClient smartThingsOAuthClient;
    private final SmartThingsTokenRepository smartThingsTokenRepository;
    private final SmartThingsEnvironment smartThingsEnvironment;

    @Override
    @Transactional
    public void execute(String code, String redirectUri) {
        try {
            log.info("Exchanging SmartThings authorization code for access token");

            var basicAuth = "Basic " + Base64.getEncoder()
                    .encodeToString((smartThingsEnvironment.clientId() + ":" + smartThingsEnvironment.clientSecret())
                            .getBytes(StandardCharsets.UTF_8));
            var formBody = "grant_type=" + GRANT_TYPE_AUTHORIZATION_CODE + "&code="
                    + URLEncoder.encode(code, StandardCharsets.UTF_8) + "&redirect_uri="
                    + URLEncoder.encode(redirectUri, StandardCharsets.UTF_8);

            var response = smartThingsOAuthClient.exchangeToken(basicAuth, formBody);

            saveOrUpdateToken(response);

            log.info("Successfully exchanged SmartThings token");
        } catch (Exception e) {
            log.error("Failed to exchange SmartThings token", e);
            throw new ExpectedException("SmartThings 토큰 교환에 실패했습니다: " + e.getMessage(), HttpStatus.BAD_GATEWAY);
        }
    }

    /**
     * 토큰 저장 또는 업데이트
     *
     * @param response
     *            토큰 교환 응답
     */
    private void saveOrUpdateToken(SmartThingsTokenExchangeResDto response) {
        var expiresAt = LocalDateTime.now().plusSeconds(response.expiresIn());

        var existingToken = smartThingsTokenRepository.findSingletonToken();

        if (existingToken.isPresent()) {
            var token = existingToken.get();
            token.updateTokens(response.accessToken(), response.refreshToken(), expiresAt);
            smartThingsTokenRepository.save(token);
        } else {
            var newToken = SmartThingsToken.builder().accessToken(response.accessToken())
                    .refreshToken(response.refreshToken()).expiresAt(expiresAt).build();
            smartThingsTokenRepository.save(newToken);
        }
    }
}
