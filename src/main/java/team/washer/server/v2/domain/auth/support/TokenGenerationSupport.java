package team.washer.server.v2.domain.auth.support;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import team.washer.server.v2.domain.auth.dto.response.TokenResDto;
import team.washer.server.v2.domain.auth.entity.redis.RefreshTokenEntity;
import team.washer.server.v2.domain.auth.repository.redis.RefreshTokenRedisRepository;
import team.washer.server.v2.domain.user.enums.UserRole;
import team.washer.server.v2.global.security.jwt.config.JwtEnvironment;
import team.washer.server.v2.global.security.jwt.provider.JwtTokenProvider;

@Component
@RequiredArgsConstructor
public class TokenGenerationSupport {

    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenRedisRepository refreshTokenRedisRepository;
    private final JwtEnvironment jwtEnvironment;

    public TokenResDto generate(final Long userId, final UserRole role) {
        final var accessToken = jwtTokenProvider.generateAccessToken(userId, role);
        final var refreshToken = jwtTokenProvider.generateRefreshToken(userId);

        refreshTokenRedisRepository.findById(userId).ifPresent(refreshTokenRedisRepository::delete);

        final var refreshTokenEntity = RefreshTokenEntity.builder().userId(userId).token(refreshToken)
                .ttl(jwtEnvironment.refreshTokenExpiration()).build();

        refreshTokenRedisRepository.save(refreshTokenEntity);

        return new TokenResDto(accessToken, jwtEnvironment.accessTokenExpiration(), refreshToken);
    }
}
