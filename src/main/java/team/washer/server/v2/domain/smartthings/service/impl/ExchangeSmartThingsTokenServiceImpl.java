package team.washer.server.v2.domain.smartthings.service.impl;

import java.time.LocalDateTime;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import team.washer.server.v2.domain.smartthings.dto.response.SmartThingsTokenExchangeResDto;
import team.washer.server.v2.domain.smartthings.entity.SmartThingsToken;
import team.washer.server.v2.domain.smartthings.repository.SmartThingsTokenRepository;
import team.washer.server.v2.domain.smartthings.service.ExchangeSmartThingsTokenService;
import team.washer.server.v2.global.common.error.exception.ExpectedException;
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

            var response = smartThingsOAuthClient.exchangeToken(GRANT_TYPE_AUTHORIZATION_CODE,
                    smartThingsEnvironment.clientId(),
                    smartThingsEnvironment.clientSecret(),
                    code,
                    redirectUri);

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
