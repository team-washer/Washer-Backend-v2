package team.washer.server.v2.domain.auth.service.impl;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import team.themoment.sdk.exception.ExpectedException;
import team.washer.server.v2.domain.auth.dto.request.RefreshTokenReqDto;
import team.washer.server.v2.domain.auth.dto.response.TokenResDto;
import team.washer.server.v2.domain.auth.service.RefreshTokenService;
import team.washer.server.v2.domain.auth.support.TokenGenerationSupport;
import team.washer.server.v2.domain.user.repository.UserRepository;
import team.washer.server.v2.global.security.jwt.provider.JwtTokenProvider;

@Service
@RequiredArgsConstructor
public class RefreshTokenServiceImpl implements RefreshTokenService {
    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;
    private final TokenGenerationSupport tokenGenerationSupport;

    @Override
    public TokenResDto execute(final RefreshTokenReqDto reqDto) {
        final var payload = jwtTokenProvider.parseRefreshToken(reqDto.refreshToken());
        final var userId = payload.userId();

        final var user = userRepository.findById(userId)
                .orElseThrow(() -> new ExpectedException("사용자를 찾을 수 없습니다.", HttpStatus.NOT_FOUND));

        return tokenGenerationSupport.rotate(userId, user.getRole(), reqDto.refreshToken());
    }
}
