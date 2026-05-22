package team.washer.server.v2.domain.auth.service.impl;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import team.themoment.sdk.exception.ExpectedException;
import team.washer.server.v2.domain.auth.dto.request.RefreshTokenReqDto;
import team.washer.server.v2.domain.auth.dto.response.TokenStatusResDto;
import team.washer.server.v2.domain.auth.repository.redis.RefreshTokenRedisRepository;
import team.washer.server.v2.domain.auth.service.CheckTokenStatusService;
import team.washer.server.v2.global.security.jwt.provider.JwtTokenProvider;

@Service
@RequiredArgsConstructor
public class CheckTokenStatusServiceImpl implements CheckTokenStatusService {
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenRedisRepository refreshTokenRedisRepository;

    @Override
    @Transactional(readOnly = true)
    public TokenStatusResDto execute(final RefreshTokenReqDto reqDto) {
        try {
            final var payload = jwtTokenProvider.parseRefreshToken(reqDto.refreshToken());
            final var valid = refreshTokenRedisRepository.findByToken(reqDto.refreshToken())
                    .filter(entity -> entity.getUserId().equals(payload.userId())).isPresent();
            return new TokenStatusResDto(valid);
        } catch (final ExpectedException e) {
            return new TokenStatusResDto(false);
        }
    }
}
