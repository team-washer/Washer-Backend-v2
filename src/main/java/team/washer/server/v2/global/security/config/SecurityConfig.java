package team.washer.server.v2.global.security.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.servlet.HandlerExceptionResolver;

import jakarta.servlet.http.HttpServletRequest;
import team.washer.server.v2.global.common.error.exception.ErrorCodeException;
import team.washer.server.v2.global.security.jwt.filter.JwtAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {
    private final DomainAuthorizationConfig domainAuthorizationConfig;
    private final CorsConfigurationSource corsConfigurationSource;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final HandlerExceptionResolver handlerExceptionResolver;

    public SecurityConfig(DomainAuthorizationConfig domainAuthorizationConfig,
            @Qualifier("configure") CorsConfigurationSource corsConfigurationSource,
            JwtAuthenticationFilter jwtAuthenticationFilter,
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver handlerExceptionResolver) {
        this.domainAuthorizationConfig = domainAuthorizationConfig;
        this.corsConfigurationSource = corsConfigurationSource;
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.handlerExceptionResolver = handlerExceptionResolver;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable).cors(cors -> cors.configurationSource(corsConfigurationSource))
                .httpBasic(AbstractHttpConfigurer::disable).formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(domainAuthorizationConfig::configure)
                // 미인증은 401, 권한 부족은 403으로 응답하도록 전역 예외 처리기에 위임해 응답 형식을 통일한다
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint((request, response, ex) -> handlerExceptionResolver
                                .resolveException(request, response, null, resolveAuthenticationFailure(request, ex)))
                        .accessDeniedHandler((request, response, ex) -> handlerExceptionResolver
                                .resolveException(request, response, null, ex)))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * JWT 필터가 토큰 파싱 오류를 남겼다면 그 오류로 응답합니다.
     * <p>
     * 클라이언트는 {@code ACCESS_TOKEN_EXPIRED}를 보고 토큰 재발급을 시작하므로, 이 코드가
     * {@code AUTHENTICATION_REQUIRED}로 바뀌지 않도록 보관된 오류를 우선합니다.
     * </p>
     */
    private static Exception resolveAuthenticationFailure(final HttpServletRequest request,
            final AuthenticationException ex) {
        return request.getAttribute(
                JwtAuthenticationFilter.AUTHENTICATION_ERROR_ATTRIBUTE) instanceof final ErrorCodeException tokenError
                        ? tokenError
                        : ex;
    }
}
