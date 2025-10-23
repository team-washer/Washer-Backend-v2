package team.washer.server.v2.global.config;

import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Configuration;

import team.washer.server.v2.global.security.data.CorsEnvironment;

@Configuration
@ConfigurationPropertiesScan(basePackageClasses = CorsEnvironment.class)
public class PropertiesScanConfig {
}
