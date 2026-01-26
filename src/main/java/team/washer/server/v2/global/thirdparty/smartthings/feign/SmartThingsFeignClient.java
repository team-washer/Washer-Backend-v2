package team.washer.server.v2.global.thirdparty.smartthings.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import team.washer.server.v2.domain.smartthings.dto.request.SmartThingsCommandReqDto;
import team.washer.server.v2.domain.smartthings.dto.response.SmartThingsDeviceStatusResDto;
import team.washer.server.v2.global.thirdparty.smartthings.config.SmartThingsFeignConfig;

@FeignClient(name = "smartthings-api", url = "${third-party.smartthings.api-url}", configuration = SmartThingsFeignConfig.class)
public interface SmartThingsFeignClient {

    @GetMapping(value = "/v1/devices/{deviceId}/status", produces = MediaType.APPLICATION_JSON_VALUE)
    SmartThingsDeviceStatusResDto getDeviceStatus(@RequestHeader("Authorization") String authorization,
            @PathVariable String deviceId);

    @PostMapping(value = "/v1/devices/{deviceId}/commands", consumes = MediaType.APPLICATION_JSON_VALUE)
    void sendDeviceCommand(@RequestHeader("Authorization") String authorization,
            @PathVariable String deviceId,
            @RequestBody SmartThingsCommandReqDto command);
}
