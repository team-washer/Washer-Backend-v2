package team.washer.server.v2.global.config;

import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Configuration;

import team.washer.server.v2.global.security.data.CorsEnvironment;
import team.washer.server.v2.global.security.jwt.config.JwtEnvironment;
import team.washer.server.v2.global.thirdparty.datagsm.config.DataGsmEnvironment;

@Configuration
@ConfigurationPropertiesScan(basePackageClasses = {CorsEnvironment.class, DataGsmEnvironment.class,
        JwtEnvironment.class})
public class PropertiesScanConfig {
}
