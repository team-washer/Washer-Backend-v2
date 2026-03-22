package team.washer.server.v2.global.security.jwt.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "jwt")
public record JwtEnvironment(String secret, Long accessTokenExpiration, Long refreshTokenExpiration) {
    public JwtEnvironment {
        if (secret == null || secret.length() < 32) {
            throw new IllegalArgumentException("JWT secret은 최소 32자 이상이어야 합니다");
        }
    }
}
