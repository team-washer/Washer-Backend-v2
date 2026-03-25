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
import team.washer.server.v2.domain.auth.dto.response.TokenResDto;
import team.washer.server.v2.domain.auth.entity.redis.RefreshTokenEntity;
import team.washer.server.v2.domain.auth.repository.redis.RefreshTokenRedisRepository;
import team.washer.server.v2.domain.auth.service.impl.RefreshTokenServiceImpl;
import team.washer.server.v2.domain.auth.support.TokenGenerationSupport;
import team.washer.server.v2.domain.user.entity.User;
import team.washer.server.v2.domain.user.enums.UserRole;
import team.washer.server.v2.domain.user.repository.UserRepository;
import team.washer.server.v2.global.security.jwt.dto.JwtPayload;
import team.washer.server.v2.global.security.jwt.provider.JwtTokenProvider;

@ExtendWith(MockitoExtension.class)
@DisplayName("RefreshTokenServiceImpl 클래스의")
class RefreshTokenServiceImplTest {

    @InjectMocks
    private RefreshTokenServiceImpl refreshTokenService;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private RefreshTokenRedisRepository refreshTokenRedisRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private TokenGenerationSupport tokenGenerationSupport;

    private User createUser(final Long userId, final UserRole role) {
        return User.builder().name("김철수").studentId("2021000" + userId).roomNumber("30" + userId).grade(3).floor(3)
                .penaltyCount(0).role(role).build();
    }

    private RefreshTokenEntity createRefreshTokenEntity(final Long userId, final String token) {
        return RefreshTokenEntity.builder().userId(userId).token(token).ttl(2592000L).build();
    }

    private RefreshTokenReqDto createRefreshTokenReqDto(final String token) {
        return new RefreshTokenReqDto(token);
    }

    private JwtPayload createJwtPayload(final Long userId, final UserRole role) {
        return new JwtPayload(userId, role);
    }

    @Nested
    @DisplayName("execute 메서드는")
    class Describe_execute {

        @Nested
        @DisplayName("유효한 Refresh Token으로 요청할 때")
        class Context_with_valid_refresh_token {

            @Test
            @DisplayName("새로운 AccessToken과 RefreshToken을 반환해야 한다")
            void it_returns_new_tokens() {
                // Given
                String oldRefreshToken = "old.refresh.token";
                Long userId = 1L;
                UserRole role = UserRole.USER;

                RefreshTokenReqDto reqDto = createRefreshTokenReqDto(oldRefreshToken);
                JwtPayload payload = createJwtPayload(userId, role);
                User user = createUser(userId, role);
                RefreshTokenEntity refreshTokenEntity = createRefreshTokenEntity(userId, oldRefreshToken);
                TokenResDto newTokens = new TokenResDto("new.access.token", 3600L, "new.refresh.token");

                given(jwtTokenProvider.parseRefreshToken(oldRefreshToken)).willReturn(payload);
                given(refreshTokenRedisRepository.findByToken(oldRefreshToken))
                        .willReturn(Optional.of(refreshTokenEntity));
                given(userRepository.findById(userId)).willReturn(Optional.of(user));
                given(tokenGenerationSupport.generate(userId, role)).willReturn(newTokens);

                // When
                TokenResDto result = refreshTokenService.execute(reqDto);

                // Then
                assertThat(result).isNotNull();
                assertThat(result.accessToken()).isEqualTo("new.access.token");
                assertThat(result.refreshToken()).isEqualTo("new.refresh.token");
                assertThat(result.expiresIn()).isEqualTo(3600L);

                then(jwtTokenProvider).should(times(1)).parseRefreshToken(oldRefreshToken);
                then(refreshTokenRedisRepository).should(times(1)).findByToken(oldRefreshToken);
                then(userRepository).should(times(1)).findById(userId);
                then(tokenGenerationSupport).should(times(1)).generate(userId, role);
            }
        }

        @Nested
        @DisplayName("만료된 Refresh Token으로 요청할 때")
        class Context_with_expired_refresh_token {

            @Test
            @DisplayName("ExpectedException이 발생해야 한다")
            void it_throws_expected_exception() {
                // Given
                String expiredToken = "expired.refresh.token";
                RefreshTokenReqDto reqDto = createRefreshTokenReqDto(expiredToken);

                given(jwtTokenProvider.parseRefreshToken(expiredToken))
                        .willThrow(new ExpectedException("JWT 토큰이 만료되었습니다.", HttpStatus.UNAUTHORIZED));

                // When & Then
                assertThatThrownBy(() -> refreshTokenService.execute(reqDto)).isInstanceOf(ExpectedException.class)
                        .hasMessage("JWT 토큰이 만료되었습니다.").satisfies(exception -> {
                            ExpectedException ex = (ExpectedException) exception;
                            assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
                        });

                then(jwtTokenProvider).should(times(1)).parseRefreshToken(expiredToken);
                then(refreshTokenRedisRepository).shouldHaveNoInteractions();
                then(userRepository).shouldHaveNoInteractions();
                then(tokenGenerationSupport).shouldHaveNoInteractions();
            }
        }

        @Nested
        @DisplayName("Redis에 없는 Refresh Token으로 요청할 때")
        class Context_with_token_not_in_redis {

            @Test
            @DisplayName("ExpectedException이 발생하고 UNAUTHORIZED 상태를 반환해야 한다")
            void it_throws_expected_exception_with_unauthorized() {
                // Given
                String unknownToken = "unknown.refresh.token";
                Long userId = 1L;
                RefreshTokenReqDto reqDto = createRefreshTokenReqDto(unknownToken);
                JwtPayload payload = createJwtPayload(userId, UserRole.USER);

                given(jwtTokenProvider.parseRefreshToken(unknownToken)).willReturn(payload);
                given(refreshTokenRedisRepository.findByToken(unknownToken)).willReturn(Optional.empty());

                // When & Then
                assertThatThrownBy(() -> refreshTokenService.execute(reqDto)).isInstanceOf(ExpectedException.class)
                        .hasMessage("유효하지 않은 Refresh Token입니다.").satisfies(exception -> {
                            ExpectedException ex = (ExpectedException) exception;
                            assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
                        });

                then(jwtTokenProvider).should(times(1)).parseRefreshToken(unknownToken);
                then(refreshTokenRedisRepository).should(times(1)).findByToken(unknownToken);
                then(userRepository).shouldHaveNoInteractions();
                then(tokenGenerationSupport).shouldHaveNoInteractions();
            }
        }

        @Nested
        @DisplayName("사용자가 삭제된 경우")
        class Context_with_deleted_user {

            @Test
            @DisplayName("ExpectedException이 발생하고 NOT_FOUND 상태를 반환해야 한다")
            void it_throws_expected_exception_with_not_found() {
                // Given
                String validToken = "valid.refresh.token";
                Long deletedUserId = 999L;
                RefreshTokenReqDto reqDto = createRefreshTokenReqDto(validToken);
                JwtPayload payload = createJwtPayload(deletedUserId, UserRole.USER);
                RefreshTokenEntity refreshTokenEntity = createRefreshTokenEntity(deletedUserId, validToken);

                given(jwtTokenProvider.parseRefreshToken(validToken)).willReturn(payload);
                given(refreshTokenRedisRepository.findByToken(validToken)).willReturn(Optional.of(refreshTokenEntity));
                given(userRepository.findById(deletedUserId)).willReturn(Optional.empty());

                // When & Then
                assertThatThrownBy(() -> refreshTokenService.execute(reqDto)).isInstanceOf(ExpectedException.class)
                        .hasMessage("사용자를 찾을 수 없습니다.").satisfies(exception -> {
                            ExpectedException ex = (ExpectedException) exception;
                            assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
                        });

                then(jwtTokenProvider).should(times(1)).parseRefreshToken(validToken);
                then(refreshTokenRedisRepository).should(times(1)).findByToken(validToken);
                then(userRepository).should(times(1)).findById(deletedUserId);
                then(tokenGenerationSupport).shouldHaveNoInteractions();
            }
        }
    }
}
