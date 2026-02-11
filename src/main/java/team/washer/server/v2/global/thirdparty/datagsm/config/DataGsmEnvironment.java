package team.washer.server.v2.global.thirdparty.datagsm.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "third-party.datagsm")
public record DataGsmEnvironment(String clientSecret) {
    public DataGsmEnvironment {
        if (clientSecret == null || clientSecret.isBlank()) {
            throw new IllegalArgumentException("DataGsm OAuth Client Secret is required");
        }
    }
}
