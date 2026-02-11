package team.washer.server.v2.global.security.jwt.provider;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import team.washer.server.v2.domain.user.enums.UserRole;
import team.washer.server.v2.global.common.error.exception.ExpectedException;
import team.washer.server.v2.global.security.jwt.config.JwtEnvironment;
import team.washer.server.v2.global.security.jwt.dto.JwtPayload;

@Component
@RequiredArgsConstructor
public class JwtTokenProvider {
    private final JwtEnvironment jwtEnvironment;

    private SecretKey secretKey;

    @PostConstruct
    void initSecretKey() {
        secretKey = Keys.hmacShaKeyFor(jwtEnvironment.secret().getBytes(StandardCharsets.UTF_8));
    }

    public String generateAccessToken(final Long userId, final UserRole role) {
        final var now = Instant.now();
        final var expiresAt = now.plusSeconds(jwtEnvironment.accessTokenExpiration());

        return Jwts.builder().subject(userId.toString()).claim("role", role.name()).issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt)).signWith(secretKey).compact();
    }

    public String generateRefreshToken(final Long userId) {
        final var now = Instant.now();
        final var expiresAt = now.plusSeconds(jwtEnvironment.refreshTokenExpiration());

        return Jwts.builder().subject(userId.toString()).issuedAt(Date.from(now)).expiration(Date.from(expiresAt))
                .signWith(secretKey).compact();
    }

    public JwtPayload parseToken(final String token) {
        try {
            final Claims claims = Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token).getPayload();

            final var userId = Long.parseLong(claims.getSubject());
            final var roleString = claims.get("role", String.class);
            final var role = roleString != null ? UserRole.valueOf(roleString) : null;

            return new JwtPayload(userId, role);
        } catch (final ExpiredJwtException e) {
            throw new ExpectedException("JWT 토큰이 만료되었습니다.", HttpStatus.UNAUTHORIZED);
        } catch (final JwtException | IllegalArgumentException e) {
            throw new ExpectedException("유효하지 않은 JWT 토큰입니다.", HttpStatus.UNAUTHORIZED);
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
}
