package team.washer.server.v2.global.security.config;

import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
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
                // 헬스 체크 및 비인증 공개 엔드포인트
                .requestMatchers("/api/v2/health", "/api/v2/app-versions/status", "/api/v2/events/datagsm").permitAll()
                // SmartThings OAuth 콜백만 공개로 유지한다.
                // SmartThings가 인증 헤더 없이 리다이렉트로 직접 진입하는 경로이므로 인가를 요구할 수 없으며,
                // CSRF 방어는 컨트롤러의 일회성 state 검증이 담당한다.
                // 인증 시작(/oauth/authorize)과 기기 수동 동기화(/devices/sync)는 관리자 전용 운영 기능이므로
                // 아래 /api/v2/admin/** 규칙으로 낙하시켜 권한 검사를 받게 한다.
                .requestMatchers(HttpMethod.GET, "/api/v2/admin/smartthings/oauth/callback").permitAll()
                // 인증 엔드포인트
                .requestMatchers("/api/v2/auth/login", "/api/v2/auth/refresh", "/api/v2/auth/token/status").permitAll()
                // 관리자 엔드포인트
                .requestMatchers("/api/v2/admin/**").hasAnyAuthority("DORMITORY_COUNCIL", "ADMIN")
                // 그 외 엔드포인트 - 인증 필요
                .anyRequest().authenticated();
    }
}
