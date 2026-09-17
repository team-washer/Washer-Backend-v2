package team.washer.server.v2.global.security.jwt.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import team.themoment.sdk.exception.ExpectedException;
import team.washer.server.v2.global.security.jwt.config.JwtEnvironment;

class JwtTokenProviderTest {
    private JwtTokenProvider jwtTokenProvider;

    @BeforeEach
    void setUp() {
        jwtTokenProvider = new JwtTokenProvider(
                new JwtEnvironment("01234567890123456789012345678901", 3600L, 2592000L));
        jwtTokenProvider.initSecretKey();
    }

    @Test
    void refreshTokensIssuedForTheSameUserAreUnique() {
        final var firstToken = jwtTokenProvider.generateRefreshToken(1L);
        final var secondToken = jwtTokenProvider.generateRefreshToken(1L);

        assertThat(firstToken).isNotEqualTo(secondToken);
        assertThat(jwtTokenProvider.parseRefreshToken(firstToken).userId()).isEqualTo(1L);
        assertThat(jwtTokenProvider.parseRefreshToken(secondToken).userId()).isEqualTo(1L);
    }

    @Test
    void expiredRefreshTokenIsRejected() {
        final var expiredProvider = new JwtTokenProvider(
                new JwtEnvironment("01234567890123456789012345678901", 3600L, -1L));
        expiredProvider.initSecretKey();
        final var expiredToken = expiredProvider.generateRefreshToken(1L);

        assertThatThrownBy(() -> expiredProvider.parseRefreshToken(expiredToken)).isInstanceOf(ExpectedException.class)
                .satisfies(exception -> assertThat(((ExpectedException) exception).getStatusCode())
                        .isEqualTo(HttpStatus.UNAUTHORIZED));
    }
}
