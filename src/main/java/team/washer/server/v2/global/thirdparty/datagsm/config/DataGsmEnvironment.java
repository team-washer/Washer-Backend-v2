package team.washer.server.v2.global.thirdparty.datagsm.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "third-party.datagsm")
public record DataGsmEnvironment(String clientId, String clientSecret, String redirectUri, String apiKey) {
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

    /**
     * OpenAPI 호출용 API 키가 설정되어 있는지 여부를 반환합니다.
     *
     * @return API 키가 존재하면 true
     */
    public boolean hasApiKey() {
        return apiKey != null && !apiKey.isBlank();
    }
}
