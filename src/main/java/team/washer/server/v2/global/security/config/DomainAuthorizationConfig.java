package team.washer.server.v2.global.security.config;

import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;
import org.springframework.stereotype.Component;

@Component
public class DomainAuthorizationConfig {
    public void configure(
            AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry authorizeRequests) {
        authorizeRequests
                // Swagger UI
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                // 헬스 체크
                .requestMatchers("/api/v2/health", "/api/v2/admin/smartthings/**").permitAll()
                // 인증 엔드포인트
                .requestMatchers("/api/v2/auth/login", "/api/v2/auth/refresh").permitAll()
                // 관리자 엔드포인트
                .requestMatchers("/api/v2/admin/**").hasAnyAuthority("DORMITORY_COUNCIL", "ADMIN")
                // 그 외 엔드포인트 - 인증 필요
                .anyRequest().authenticated();
    }
}
