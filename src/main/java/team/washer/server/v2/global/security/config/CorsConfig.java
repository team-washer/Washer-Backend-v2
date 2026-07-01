package team.washer.server.v2.global.security.config;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import team.washer.server.v2.global.security.data.CorsEnvironment;

@Configuration
@EnableConfigurationProperties(CorsEnvironment.class)
public class CorsConfig {
    private static final String ALL_ORIGINS = "*";

    private final CorsEnvironment corsEnvironment;

    public CorsConfig(CorsEnvironment corsEnvironment) {
        this.corsEnvironment = corsEnvironment;
    }

    @Bean
    public CorsConfigurationSource configure() {
        CorsConfiguration configuration = new CorsConfiguration();
        final var allowedOriginPatterns = emptyIfNull(corsEnvironment.getAllowedOriginPatterns());
        final var allowedOrigins = emptyIfNull(corsEnvironment.getAllowedOrigins());

        if (allowedOriginPatterns.isEmpty()) {
            validateAllowedOrigins(allowedOrigins);
            configuration.setAllowedOrigins(allowedOrigins);
        } else {
            configuration.setAllowedOriginPatterns(allowedOriginPatterns);
        }
        configuration.setAllowedMethods(
                Arrays.stream(HttpMethod.values()).map(HttpMethod::name).collect(Collectors.toList()));
        configuration.addAllowedHeader("*");
        configuration.setAllowCredentials(corsEnvironment.isAllowCredentials());

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    private void validateAllowedOrigins(List<String> allowedOrigins) {
        if (corsEnvironment.isAllowCredentials() && allowedOrigins.contains(ALL_ORIGINS)) {
            throw new IllegalStateException("인증 정보를 허용할 때는 모든 Origin을 허용할 수 없습니다.");
        }
    }

    private static List<String> emptyIfNull(List<String> values) {
        return values == null ? List.of() : values;
    }
}
