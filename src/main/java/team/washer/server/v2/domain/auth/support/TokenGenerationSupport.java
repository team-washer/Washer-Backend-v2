package team.washer.server.v2.domain.auth.support;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import team.washer.server.v2.domain.auth.dto.response.TokenResDto;
import team.washer.server.v2.domain.auth.repository.redis.RefreshTokenRedisStore;
import team.washer.server.v2.domain.user.enums.UserRole;
import team.washer.server.v2.global.common.error.code.ErrorCode;
import team.washer.server.v2.global.common.error.exception.ErrorCodeException;
import team.washer.server.v2.global.security.jwt.config.JwtEnvironment;
import team.washer.server.v2.global.security.jwt.provider.JwtTokenProvider;

@Component
@RequiredArgsConstructor
public class TokenGenerationSupport {

    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenRedisStore refreshTokenRedisStore;
    private final JwtEnvironment jwtEnvironment;

    public TokenResDto generate(final Long userId, final UserRole role) {
        final var accessToken = jwtTokenProvider.generateAccessToken(userId, role);
        final var refreshToken = jwtTokenProvider.generateRefreshToken(userId);

        refreshTokenRedisStore.replace(userId, refreshToken, jwtEnvironment.refreshTokenExpiration());

        return new TokenResDto(accessToken, jwtEnvironment.accessTokenExpiration(), refreshToken);
    }

    public TokenResDto rotate(final Long userId, final UserRole role, final String expectedRefreshToken) {
        final var accessToken = jwtTokenProvider.generateAccessToken(userId, role);
        final var refreshToken = jwtTokenProvider.generateRefreshToken(userId);

        final var rotated = refreshTokenRedisStore
                .rotate(userId, expectedRefreshToken, refreshToken, jwtEnvironment.refreshTokenExpiration());
        if (!rotated) {
            throw new ErrorCodeException(ErrorCode.REFRESH_TOKEN_INVALID);
        }

        return new TokenResDto(accessToken, jwtEnvironment.accessTokenExpiration(), refreshToken);
    }
}
