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

        // springdoc은 swagger-ui 정적 리소스를 swagger-ui.path의 부모 경로
        // 하위(/{prefix}/swagger-ui/**)에서 서빙한다.
        // 난독화 시 접두사가 붙으므로 해석된 경로 기준으로 리소스 경로를 허용해야 index.html 등에 접근할 수 있다.
        final var lastSlash = swaggerUiPath.lastIndexOf('/');
        final var swaggerUiResources = (lastSlash > 0 ? swaggerUiPath.substring(0, lastSlash) : "") + "/swagger-ui/**";

        authorizeRequests
                // Swagger UI
                .requestMatchers(swaggerUiResources, swaggerUiPath, apiDocsPath, apiDocsPath + "/**").permitAll()
                // 헬스 체크
                .requestMatchers("/api/v2/health", "/api/v2/admin/smartthings/**", "/api/v2/app-versions/status")
                .permitAll()
                // 인증 엔드포인트
                .requestMatchers("/api/v2/auth/login", "/api/v2/auth/refresh", "/api/v2/auth/token/status").permitAll()
                // 앱 버전 정책 관리 API (관리자 전용 기능이나 토큰 없이 접근 허용)
                .requestMatchers("/api/v2/admin/app-versions/**").permitAll()
                // 관리자 엔드포인트
                .requestMatchers("/api/v2/admin/**").hasAnyAuthority("DORMITORY_COUNCIL", "ADMIN")
                // 그 외 엔드포인트 - 인증 필요
                .anyRequest().authenticated();
    }
}
