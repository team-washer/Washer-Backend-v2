package team.washer.server.v2.global.security.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

@Configuration
@EnableMethodSecurity(prePostEnabled = true, securedEnabled = true)
public class MethodSecurityConfig {
    // 메서드 레벨 보안 활성화
    // @PreAuthorize, @PostAuthorize, @Secured 어노테이션 사용 가능
}
