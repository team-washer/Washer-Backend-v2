package team.washer.server.v2.domain.auth.service.impl;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import team.themoment.sdk.exception.ExpectedException;
import team.washer.server.v2.domain.auth.dto.request.RefreshTokenReqDto;
import team.washer.server.v2.domain.auth.dto.response.TokenResDto;
import team.washer.server.v2.domain.auth.repository.redis.RefreshTokenRedisRepository;
import team.washer.server.v2.domain.auth.service.GenerateTokenService;
import team.washer.server.v2.domain.auth.service.RefreshTokenService;
import team.washer.server.v2.domain.user.repository.UserRepository;
import team.washer.server.v2.global.security.jwt.provider.JwtTokenProvider;

@Service
@RequiredArgsConstructor
public class RefreshTokenServiceImpl implements RefreshTokenService {
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenRedisRepository refreshTokenRedisRepository;
    private final UserRepository userRepository;
    private final GenerateTokenService generateTokenService;

    @Override
    public TokenResDto execute(final RefreshTokenReqDto reqDto) {
        final var payload = jwtTokenProvider.parseRefreshToken(reqDto.refreshToken());
        final var userId = payload.userId();

        final var refreshTokenEntity = refreshTokenRedisRepository.findByToken(reqDto.refreshToken())
                .orElseThrow(() -> new ExpectedException("유효하지 않은 Refresh Token입니다.", HttpStatus.UNAUTHORIZED));

        if (!refreshTokenEntity.getUserId().equals(userId)) {
            throw new ExpectedException("유효하지 않은 Refresh Token입니다.", HttpStatus.UNAUTHORIZED);
        }

        final var user = userRepository.findById(userId)
                .orElseThrow(() -> new ExpectedException("사용자를 찾을 수 없습니다.", HttpStatus.NOT_FOUND));

        refreshTokenRedisRepository.delete(refreshTokenEntity);

        return generateTokenService.execute(userId, user.getRole());
    }
}
