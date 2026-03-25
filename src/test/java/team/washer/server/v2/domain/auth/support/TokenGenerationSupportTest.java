package team.washer.server.v2.domain.auth.support;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import team.washer.server.v2.domain.auth.dto.response.TokenResDto;
import team.washer.server.v2.domain.auth.entity.redis.RefreshTokenEntity;
import team.washer.server.v2.domain.auth.repository.redis.RefreshTokenRedisRepository;
import team.washer.server.v2.domain.user.enums.UserRole;
import team.washer.server.v2.global.security.jwt.config.JwtEnvironment;
import team.washer.server.v2.global.security.jwt.provider.JwtTokenProvider;

@ExtendWith(MockitoExtension.class)
@DisplayName("TokenGenerationSupport 클래스의")
class TokenGenerationSupportTest {

    @InjectMocks
    private TokenGenerationSupport tokenGenerationSupport;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private RefreshTokenRedisRepository refreshTokenRedisRepository;

    @Mock
    private JwtEnvironment jwtEnvironment;

    @Nested
    @DisplayName("generate 메서드는")
    class Describe_generate {

        @Nested
        @DisplayName("첫 로그인 시")
        class Context_with_first_login {

            @Test
            @DisplayName("Redis에 새로운 RefreshToken이 저장되어야 한다")
            void it_creates_new_refresh_token_in_redis() {
                // Given
                Long userId = 1L;
                UserRole role = UserRole.USER;
                String accessToken = "mock.access.token";
                String refreshToken = "mock.refresh.token";
                Long accessTokenExpiration = 3600L;
                Long refreshTokenExpiration = 2592000L;

                given(jwtTokenProvider.generateAccessToken(userId, role)).willReturn(accessToken);
                given(jwtTokenProvider.generateRefreshToken(userId)).willReturn(refreshToken);
                given(jwtEnvironment.accessTokenExpiration()).willReturn(accessTokenExpiration);
                given(jwtEnvironment.refreshTokenExpiration()).willReturn(refreshTokenExpiration);

                RefreshTokenEntity savedEntity = RefreshTokenEntity.builder().userId(userId).token(refreshToken)
                        .ttl(refreshTokenExpiration).build();
                given(refreshTokenRedisRepository.save(any(RefreshTokenEntity.class))).willReturn(savedEntity);

                // When
                TokenResDto result = tokenGenerationSupport.generate(userId, role);

                // Then
                assertThat(result).isNotNull();
                assertThat(result.accessToken()).isEqualTo(accessToken);
                assertThat(result.refreshToken()).isEqualTo(refreshToken);
                assertThat(result.expiresIn()).isEqualTo(accessTokenExpiration);

                then(refreshTokenRedisRepository).should(times(1)).save(
                        argThat(entity -> entity.getUserId().equals(userId) && entity.getToken().equals(refreshToken)
                                && entity.getTtl().equals(refreshTokenExpiration)));
            }
        }

        @Nested
        @DisplayName("재로그인 시")
        class Context_with_returning_user {

            @Test
            @DisplayName("기존 RefreshToken이 덮어써져야 한다")
            void it_overwrites_existing_refresh_token_in_redis() {
                // Given
                Long userId = 1L;
                UserRole role = UserRole.USER;
                String accessToken = "new.access.token";
                String newRefreshToken = "new.refresh.token";
                Long accessTokenExpiration = 3600L;
                Long refreshTokenExpiration = 2592000L;

                given(jwtTokenProvider.generateAccessToken(userId, role)).willReturn(accessToken);
                given(jwtTokenProvider.generateRefreshToken(userId)).willReturn(newRefreshToken);
                given(jwtEnvironment.accessTokenExpiration()).willReturn(accessTokenExpiration);
                given(jwtEnvironment.refreshTokenExpiration()).willReturn(refreshTokenExpiration);

                RefreshTokenEntity savedEntity = RefreshTokenEntity.builder().userId(userId).token(newRefreshToken)
                        .ttl(refreshTokenExpiration).build();
                given(refreshTokenRedisRepository.save(any(RefreshTokenEntity.class))).willReturn(savedEntity);

                // When
                TokenResDto result = tokenGenerationSupport.generate(userId, role);

                // Then
                assertThat(result).isNotNull();
                assertThat(result.accessToken()).isEqualTo(accessToken);
                assertThat(result.refreshToken()).isEqualTo(newRefreshToken);
                assertThat(result.expiresIn()).isEqualTo(accessTokenExpiration);

                then(refreshTokenRedisRepository).should(times(1)).save(any(RefreshTokenEntity.class));
            }
        }

        @Nested
        @DisplayName("다양한 권한으로 로그인 시")
        class Context_with_different_roles {

            @Test
            @DisplayName("USER 권한으로 토큰이 생성되어야 한다")
            void it_generates_token_with_user_role() {
                // Given
                Long userId = 1L;
                UserRole role = UserRole.USER;
                given(jwtTokenProvider.generateAccessToken(userId, role)).willReturn("user.access.token");
                given(jwtTokenProvider.generateRefreshToken(userId)).willReturn("user.refresh.token");
                given(jwtEnvironment.accessTokenExpiration()).willReturn(3600L);
                given(jwtEnvironment.refreshTokenExpiration()).willReturn(2592000L);
                given(refreshTokenRedisRepository.save(any())).willReturn(null);

                // When
                TokenResDto result = tokenGenerationSupport.generate(userId, role);

                // Then
                assertThat(result).isNotNull();
                then(jwtTokenProvider).should(times(1)).generateAccessToken(userId, UserRole.USER);
            }

            @Test
            @DisplayName("ADMIN 권한으로 토큰이 생성되어야 한다")
            void it_generates_token_with_admin_role() {
                // Given
                Long userId = 2L;
                UserRole role = UserRole.ADMIN;
                given(jwtTokenProvider.generateAccessToken(userId, role)).willReturn("admin.access.token");
                given(jwtTokenProvider.generateRefreshToken(userId)).willReturn("admin.refresh.token");
                given(jwtEnvironment.accessTokenExpiration()).willReturn(3600L);
                given(jwtEnvironment.refreshTokenExpiration()).willReturn(2592000L);
                given(refreshTokenRedisRepository.save(any())).willReturn(null);

                // When
                TokenResDto result = tokenGenerationSupport.generate(userId, role);

                // Then
                assertThat(result).isNotNull();
                then(jwtTokenProvider).should(times(1)).generateAccessToken(userId, UserRole.ADMIN);
            }
        }
    }
}
