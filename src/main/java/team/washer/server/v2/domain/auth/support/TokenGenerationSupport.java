package team.washer.server.v2.domain.auth.support;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import team.washer.server.v2.domain.auth.dto.response.TokenResDto;
import team.washer.server.v2.domain.auth.entity.redis.RefreshTokenEntity;
import team.washer.server.v2.domain.auth.repository.redis.RefreshTokenRedisRepository;
import team.washer.server.v2.domain.user.enums.UserRole;
import team.washer.server.v2.global.security.jwt.config.JwtEnvironment;
import team.washer.server.v2.global.security.jwt.provider.JwtTokenProvider;

/**
 * JWT 토큰 생성을 담당하는 지원 컴포넌트.
 */
@Component
@RequiredArgsConstructor
public class TokenGenerationSupport {

    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenRedisRepository refreshTokenRedisRepository;
    private final JwtEnvironment jwtEnvironment;

    /**
     * 액세스 토큰과 리프레시 토큰을 생성한다.
     *
     * <p>
     * 기존 엔티티를 덮어쓰기(overwrite)하면 Spring Data Redis의 {@code @Indexed} 보조 인덱스가 정리되지 않아
     * 새 토큰으로 조회가 불가능해지는 문제가 있다. 이를 방지하기 위해 저장 전에 기존 엔티티를 명시적으로 삭제하여 보조 인덱스 상태를
     * 초기화한다.
     */
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
