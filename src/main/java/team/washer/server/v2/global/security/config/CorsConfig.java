package team.washer.server.v2.global.security.config;

import java.util.Arrays;
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
    private final CorsEnvironment corsEnvironment;

    public CorsConfig(CorsEnvironment corsEnvironment) {
        this.corsEnvironment = corsEnvironment;
    }

    @Bean
    public CorsConfigurationSource configure() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(corsEnvironment.getAllowedOrigins());
        configuration.setAllowedMethods(
                Arrays.stream(HttpMethod.values()).map(HttpMethod::name).collect(Collectors.toList()));
        configuration.addAllowedHeader("*");
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
