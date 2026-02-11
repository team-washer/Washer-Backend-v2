package team.washer.server.v2.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import team.themoment.datagsm.sdk.oauth.DataGsmClient;
import team.washer.server.v2.global.thirdparty.datagsm.config.DataGsmEnvironment;

@Configuration
public class OauthConfig {
    @Bean
    public DataGsmClient dataGsmClient(final DataGsmEnvironment dataGsmEnvironment) {
        return DataGsmClient.builder(dataGsmEnvironment.clientSecret()).build();
    }
}
