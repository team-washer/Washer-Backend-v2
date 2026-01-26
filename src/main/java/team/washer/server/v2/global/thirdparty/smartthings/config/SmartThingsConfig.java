package team.washer.server.v2.global.thirdparty.smartthings.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Configuration;

import lombok.Getter;
import lombok.Setter;

@Configuration
@EnableFeignClients(basePackages = {"team.washer.server.v2.global.thirdparty.smartthings"})
@ConfigurationProperties(prefix = "third-party.smartthings")
@Getter
@Setter
public class SmartThingsConfig {
    private String apiUrl = "https://api.smartthings.com";
    private String oauthUrl = "https://auth-global.api.smartthings.com/oauth/token";
    private String clientId;
    private String clientSecret;
}
