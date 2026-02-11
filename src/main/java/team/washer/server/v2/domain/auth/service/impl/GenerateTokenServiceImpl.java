package team.washer.server.v2.domain.auth.service.impl;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import team.washer.server.v2.domain.auth.dto.response.TokenResDto;
import team.washer.server.v2.domain.auth.entity.redis.RefreshTokenEntity;
import team.washer.server.v2.domain.auth.repository.redis.RefreshTokenRedisRepository;
import team.washer.server.v2.domain.auth.service.GenerateTokenService;
import team.washer.server.v2.domain.user.enums.UserRole;
import team.washer.server.v2.global.security.jwt.config.JwtEnvironment;
import team.washer.server.v2.global.security.jwt.provider.JwtTokenProvider;

@Service
@RequiredArgsConstructor
public class GenerateTokenServiceImpl implements GenerateTokenService {
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenRedisRepository refreshTokenRedisRepository;
    private final JwtEnvironment jwtEnvironment;

    @Override
    public TokenResDto execute(final Long userId, final UserRole role) {
        final var accessToken = jwtTokenProvider.generateAccessToken(userId, role);
        final var refreshToken = jwtTokenProvider.generateRefreshToken(userId);

        final var refreshTokenEntity = RefreshTokenEntity.builder().userId(userId).token(refreshToken)
                .ttl(jwtEnvironment.refreshTokenExpiration()).build();

        refreshTokenRedisRepository.save(refreshTokenEntity);

        return new TokenResDto(accessToken, jwtEnvironment.accessTokenExpiration(), refreshToken);
    }
}
