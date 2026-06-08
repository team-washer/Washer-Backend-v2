package team.washer.server.v2.domain.smartthings.service.impl;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import team.themoment.sdk.exception.ExpectedException;
import team.washer.server.v2.domain.smartthings.dto.response.SmartThingsAccessTokenResDto;
import team.washer.server.v2.domain.smartthings.repository.SmartThingsTokenRepository;
import team.washer.server.v2.domain.smartthings.service.QuerySmartThingsAccessTokenService;

/**
 * SmartThings 액세스 토큰 조회 서비스 구현체
 *
 * <p>
 * 단일 토큰을 조회하여 유효성을 검증한 뒤 액세스 토큰과 만료 시각을 반환한다. 토큰이 없거나 만료된 경우 예외를 발생시킨다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class QuerySmartThingsAccessTokenServiceImpl implements QuerySmartThingsAccessTokenService {

    private final SmartThingsTokenRepository tokenRepository;

    @Override
    @Transactional(readOnly = true)
    public SmartThingsAccessTokenResDto execute() {
        final var token = tokenRepository.findSingletonToken()
                .orElseThrow(() -> new ExpectedException("SmartThings 토큰이 존재하지 않습니다", HttpStatus.NOT_FOUND));
        if (!token.isValid()) {
            throw new ExpectedException("SmartThings 토큰이 만료되었거나 유효하지 않습니다", HttpStatus.NOT_FOUND);
        }
        log.info("smartthings access token queried expiresAt={}", token.getExpiresAt());
        return new SmartThingsAccessTokenResDto(token.getAccessToken(), token.getExpiresAt());
    }
}
