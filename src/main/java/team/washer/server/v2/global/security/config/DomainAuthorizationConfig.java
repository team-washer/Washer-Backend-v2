package team.washer.server.v2.global.security.config;

import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;
import org.springframework.stereotype.Component;

@Component
public class DomainAuthorizationConfig {
    public void configure(
            AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry authorizeRequests) {
        authorizeRequests
                // Swagger
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                // Health Check
                .requestMatchers("/api/v2/health", "/api/v2/admin/smartthings/**").permitAll()
                // Auth endpoints
                .requestMatchers("/api/v2/auth/login", "/api/v2/auth/refresh").permitAll()
                // Admin endpoints
                .requestMatchers("/api/v2/admin/**").hasAnyAuthority("DORMITORY_COUNCIL", "ADMIN")
                // Other endpoints - 인증 필요
                .anyRequest().authenticated();
    }
}
