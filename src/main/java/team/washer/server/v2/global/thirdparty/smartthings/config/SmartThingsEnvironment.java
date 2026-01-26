package team.washer.server.v2.global.thirdparty.smartthings.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "third-party.smartthings")
public record SmartThingsEnvironment(String apiUrl, String oauthUrl, String clientId, String clientSecret) {
    public SmartThingsEnvironment {
        if (apiUrl == null || apiUrl.isBlank()) {
            apiUrl = "https://api.smartthings.com";
        }
        if (oauthUrl == null || oauthUrl.isBlank()) {
            oauthUrl = "https://auth-global.api.smartthings.com/oauth/token";
        }
    }
}
