package team.washer.server.v2.domain.auth.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import team.themoment.sdk.exception.ExpectedException;
import team.washer.server.v2.domain.auth.dto.request.RefreshTokenReqDto;
import team.washer.server.v2.domain.auth.dto.response.TokenStatusResDto;
import team.washer.server.v2.domain.auth.entity.redis.RefreshTokenEntity;
import team.washer.server.v2.domain.auth.repository.redis.RefreshTokenRedisRepository;
import team.washer.server.v2.domain.auth.service.impl.CheckTokenStatusServiceImpl;
import team.washer.server.v2.global.security.jwt.dto.JwtPayload;
import team.washer.server.v2.global.security.jwt.provider.JwtTokenProvider;

@ExtendWith(MockitoExtension.class)
@DisplayName("CheckTokenStatusServiceImpl 클래스의")
class CheckTokenStatusServiceImplTest {

    @InjectMocks
    private CheckTokenStatusServiceImpl checkTokenStatusService;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private RefreshTokenRedisRepository refreshTokenRedisRepository;

    private RefreshTokenReqDto createReqDto(final String token) {
        return new RefreshTokenReqDto(token);
    }

    private JwtPayload createJwtPayload(final Long userId) {
        return new JwtPayload(userId, null);
    }

    private RefreshTokenEntity createRefreshTokenEntity(final Long userId, final String token) {
        return RefreshTokenEntity.builder().userId(userId).token(token).ttl(2592000L).build();
    }

    @Nested
    @DisplayName("execute 메서드는")
    class Describe_execute {

        @Nested
        @DisplayName("유효한 Refresh Token으로 요청할 때")
        class Context_with_valid_refresh_token {

            @Test
            @DisplayName("valid가 true인 응답을 반환해야 한다")
            void it_returns_valid_true() {
                // Given
                String token = "valid.refresh.token";
                Long userId = 1L;
                RefreshTokenReqDto reqDto = createReqDto(token);
                JwtPayload payload = createJwtPayload(userId);
                RefreshTokenEntity entity = createRefreshTokenEntity(userId, token);

                given(jwtTokenProvider.parseRefreshToken(token)).willReturn(payload);
                given(refreshTokenRedisRepository.findByToken(token)).willReturn(Optional.of(entity));

                // When
                TokenStatusResDto result = checkTokenStatusService.execute(reqDto);

                // Then
                assertThat(result.valid()).isTrue();
                then(jwtTokenProvider).should(times(1)).parseRefreshToken(token);
                then(refreshTokenRedisRepository).should(times(1)).findByToken(token);
            }
        }

        @Nested
        @DisplayName("만료된 Refresh Token으로 요청할 때")
        class Context_with_expired_refresh_token {

            @Test
            @DisplayName("valid가 false인 응답을 반환해야 한다")
            void it_returns_valid_false() {
                // Given
                String token = "expired.refresh.token";
                RefreshTokenReqDto reqDto = createReqDto(token);

                given(jwtTokenProvider.parseRefreshToken(token))
                        .willThrow(new ExpectedException("JWT 토큰이 만료되었습니다.", HttpStatus.UNAUTHORIZED));

                // When
                TokenStatusResDto result = checkTokenStatusService.execute(reqDto);

                // Then
                assertThat(result.valid()).isFalse();
                then(jwtTokenProvider).should(times(1)).parseRefreshToken(token);
                then(refreshTokenRedisRepository).shouldHaveNoInteractions();
            }
        }

        @Nested
        @DisplayName("위조된 Refresh Token으로 요청할 때")
        class Context_with_invalid_refresh_token {

            @Test
            @DisplayName("valid가 false인 응답을 반환해야 한다")
            void it_returns_valid_false() {
                // Given
                String token = "forged.refresh.token";
                RefreshTokenReqDto reqDto = createReqDto(token);

                given(jwtTokenProvider.parseRefreshToken(token))
                        .willThrow(new ExpectedException("유효하지 않은 JWT 토큰입니다.", HttpStatus.UNAUTHORIZED));

                // When
                TokenStatusResDto result = checkTokenStatusService.execute(reqDto);

                // Then
                assertThat(result.valid()).isFalse();
                then(jwtTokenProvider).should(times(1)).parseRefreshToken(token);
                then(refreshTokenRedisRepository).shouldHaveNoInteractions();
            }
        }

        @Nested
        @DisplayName("Redis에 존재하지 않는 Refresh Token으로 요청할 때")
        class Context_with_token_not_in_redis {

            @Test
            @DisplayName("valid가 false인 응답을 반환해야 한다")
            void it_returns_valid_false() {
                // Given
                String token = "revoked.refresh.token";
                Long userId = 1L;
                RefreshTokenReqDto reqDto = createReqDto(token);
                JwtPayload payload = createJwtPayload(userId);

                given(jwtTokenProvider.parseRefreshToken(token)).willReturn(payload);
                given(refreshTokenRedisRepository.findByToken(token)).willReturn(Optional.empty());

                // When
                TokenStatusResDto result = checkTokenStatusService.execute(reqDto);

                // Then
                assertThat(result.valid()).isFalse();
                then(jwtTokenProvider).should(times(1)).parseRefreshToken(token);
                then(refreshTokenRedisRepository).should(times(1)).findByToken(token);
            }
        }

        @Nested
        @DisplayName("userId가 일치하지 않는 Refresh Token으로 요청할 때")
        class Context_with_mismatched_user_id {

            @Test
            @DisplayName("valid가 false인 응답을 반환해야 한다")
            void it_returns_valid_false() {
                // Given
                String token = "tampered.refresh.token";
                Long jwtUserId = 1L;
                Long redisUserId = 999L;
                RefreshTokenReqDto reqDto = createReqDto(token);
                JwtPayload payload = createJwtPayload(jwtUserId);
                RefreshTokenEntity entity = createRefreshTokenEntity(redisUserId, token);

                given(jwtTokenProvider.parseRefreshToken(token)).willReturn(payload);
                given(refreshTokenRedisRepository.findByToken(token)).willReturn(Optional.of(entity));

                // When
                TokenStatusResDto result = checkTokenStatusService.execute(reqDto);

                // Then
                assertThat(result.valid()).isFalse();
                then(jwtTokenProvider).should(times(1)).parseRefreshToken(token);
                then(refreshTokenRedisRepository).should(times(1)).findByToken(token);
            }
        }
    }
}
