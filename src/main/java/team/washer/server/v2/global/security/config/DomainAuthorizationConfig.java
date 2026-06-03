package team.washer.server.v2.global.security.config;

import org.springframework.core.env.Environment;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class DomainAuthorizationConfig {

    private static final String DEFAULT_API_DOCS_PATH = "/v3/api-docs";
    private static final String DEFAULT_SWAGGER_UI_PATH = "/swagger-ui.html";

    private final Environment environment;

    public void configure(
            AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry authorizeRequests) {
        // prod 프로파일에서는 SwaggerPathObfuscator가 경로를 난독화하므로 실제 해석된 경로를 허용한다.
        final var apiDocsPath = environment.getProperty("springdoc.api-docs.path", DEFAULT_API_DOCS_PATH);
        final var swaggerUiPath = environment.getProperty("springdoc.swagger-ui.path", DEFAULT_SWAGGER_UI_PATH);

        authorizeRequests
                // Swagger UI
                .requestMatchers("/swagger-ui/**", swaggerUiPath, apiDocsPath, apiDocsPath + "/**").permitAll()
                // 헬스 체크
                .requestMatchers("/api/v2/health", "/api/v2/admin/smartthings/**").permitAll()
                // 인증 엔드포인트
                .requestMatchers("/api/v2/auth/login", "/api/v2/auth/refresh", "/api/v2/auth/token/status").permitAll()
                // 관리자 엔드포인트
                .requestMatchers("/api/v2/admin/**").hasAnyAuthority("DORMITORY_COUNCIL", "ADMIN")
                // 그 외 엔드포인트 - 인증 필요
                .anyRequest().authenticated();
    }
}
