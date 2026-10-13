package team.washer.server.v2.global.security.jwt.provider;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.springframework.stereotype.Component;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import team.washer.server.v2.domain.user.enums.UserRole;
import team.washer.server.v2.global.common.error.code.ErrorCode;
import team.washer.server.v2.global.common.error.exception.ErrorCodeException;
import team.washer.server.v2.global.security.jwt.config.JwtEnvironment;
import team.washer.server.v2.global.security.jwt.dto.JwtPayload;

@Component
@RequiredArgsConstructor
public class JwtTokenProvider {
    private static final String TOKEN_TYPE_CLAIM = "tokenType";
    private static final String TOKEN_TYPE_ACCESS = "ACCESS";
    private static final String TOKEN_TYPE_REFRESH = "REFRESH";

    private final JwtEnvironment jwtEnvironment;

    private SecretKey secretKey;

    @PostConstruct
    void initSecretKey() {
        secretKey = Keys.hmacShaKeyFor(jwtEnvironment.secret().getBytes(StandardCharsets.UTF_8));
    }

    public String generateAccessToken(final Long userId, final UserRole role) {
        final var now = Instant.now();
        final var expiresAt = now.plusSeconds(jwtEnvironment.accessTokenExpiration());

        return Jwts.builder().subject(userId.toString()).claim("role", role.name())
                .claim(TOKEN_TYPE_CLAIM, TOKEN_TYPE_ACCESS).issuedAt(Date.from(now)).expiration(Date.from(expiresAt))
                .signWith(secretKey).compact();
    }

    public String generateRefreshToken(final Long userId) {
        final var now = Instant.now();
        final var expiresAt = now.plusSeconds(jwtEnvironment.refreshTokenExpiration());

        return Jwts.builder().subject(userId.toString()).claim(TOKEN_TYPE_CLAIM, TOKEN_TYPE_REFRESH)
                .id(UUID.randomUUID().toString()).issuedAt(Date.from(now)).expiration(Date.from(expiresAt))
                .signWith(secretKey).compact();
    }

    /** Access Token을 파싱합니다. Refresh Token이 전달되면 예외를 발생시킵니다. */
    public JwtPayload parseAccessToken(final String token) {
        final var claims = parseClaims(token, ErrorCode.ACCESS_TOKEN_EXPIRED, ErrorCode.ACCESS_TOKEN_INVALID);

        if (!TOKEN_TYPE_ACCESS.equals(claims.get(TOKEN_TYPE_CLAIM, String.class))) {
            throw new ErrorCodeException(ErrorCode.ACCESS_TOKEN_INVALID);
        }

        try {
            final var userId = Long.parseLong(claims.getSubject());
            final var roleString = claims.get("role", String.class);
            final var role = roleString != null ? UserRole.valueOf(roleString) : null;
            return new JwtPayload(userId, role);
        } catch (final IllegalArgumentException e) {
            throw new ErrorCodeException(ErrorCode.ACCESS_TOKEN_INVALID, e);
        }
    }

    /** Refresh Token을 파싱합니다. Access Token이 전달되면 예외를 발생시킵니다. */
    public JwtPayload parseRefreshToken(final String token) {
        final var claims = parseClaims(token, ErrorCode.REFRESH_TOKEN_EXPIRED, ErrorCode.REFRESH_TOKEN_INVALID);

        if (!TOKEN_TYPE_REFRESH.equals(claims.get(TOKEN_TYPE_CLAIM, String.class))) {
            throw new ErrorCodeException(ErrorCode.REFRESH_TOKEN_INVALID);
        }

        try {
            return new JwtPayload(Long.parseLong(claims.getSubject()), null);
        } catch (final IllegalArgumentException e) {
            throw new ErrorCodeException(ErrorCode.REFRESH_TOKEN_INVALID, e);
        }
    }

    public boolean validateToken(final String token) {
        try {
            Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token);
            return true;
        } catch (final JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    private Claims parseClaims(final String token, final ErrorCode expiredCode, final ErrorCode invalidCode) {
        try {
            return Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token).getPayload();
        } catch (final ExpiredJwtException e) {
            throw new ErrorCodeException(expiredCode, e);
        } catch (final JwtException | IllegalArgumentException e) {
            throw new ErrorCodeException(invalidCode, e);
        }
    }
}
