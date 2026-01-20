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
                .requestMatchers("/api/v2/health").permitAll()
                // Reservation endpoints - 인증 시스템 구현 후 활성화 예정
                // .requestMatchers("/api/v2/reservations/**").authenticated()
                // .requestMatchers("/api/v2/admin/reservations/**").hasAnyRole("DORMITORY_COUNCIL", "ADMIN")
                // Others - 현재는 모두 허용
                .anyRequest().permitAll();
    }
}
