package team.washer.server.v2.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import team.themoment.datagsm.sdk.oauth.DataGsmOAuthClient;
import team.washer.server.v2.global.thirdparty.datagsm.config.DataGsmEnvironment;

@Configuration
public class OauthConfig {
    @Bean
    public DataGsmOAuthClient dataGsmOAuthClient(final DataGsmEnvironment dataGsmEnvironment) {
        return DataGsmOAuthClient.builder(dataGsmEnvironment.clientId(), dataGsmEnvironment.clientSecret()).build();
    }
}
