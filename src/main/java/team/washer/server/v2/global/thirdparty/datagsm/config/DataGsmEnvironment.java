package team.washer.server.v2.global.thirdparty.datagsm.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "third-party.datagsm")
public record DataGsmEnvironment(String clientId, String clientSecret, String redirectUri) {
    public DataGsmEnvironment {
        if (clientSecret == null || clientSecret.isBlank()) {
            throw new IllegalArgumentException("DataGsm OAuth Client Secret is required");
        }
        if (clientId == null || clientId.isBlank()) {
            throw new IllegalArgumentException("DataGsm OAuth Client ID is required");
        }
        if (redirectUri == null || redirectUri.isBlank()) {
            throw new IllegalArgumentException("DataGsm OAuth Redirect URI is required");
        }
    }
}
