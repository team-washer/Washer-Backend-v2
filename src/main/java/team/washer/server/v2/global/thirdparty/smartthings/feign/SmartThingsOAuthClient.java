package team.washer.server.v2.global.thirdparty.smartthings.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import team.washer.server.v2.domain.smartthings.dto.response.SmartThingsTokenExchangeResDto;
import team.washer.server.v2.global.thirdparty.smartthings.config.SmartThingsFeignConfig;

@FeignClient(name = "smartthings-oauth", url = "${third-party.smartthings.oauth-url}", configuration = SmartThingsFeignConfig.class)
public interface SmartThingsOAuthClient {

    @PostMapping(consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    SmartThingsTokenExchangeResDto exchangeToken(@RequestBody MultiValueMap<String, String> formData);

    @PostMapping(consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    SmartThingsTokenExchangeResDto refreshToken(@RequestBody MultiValueMap<String, String> formData);
}
