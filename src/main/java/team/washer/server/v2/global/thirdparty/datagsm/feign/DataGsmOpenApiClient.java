package team.washer.server.v2.global.thirdparty.datagsm.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import team.washer.server.v2.global.thirdparty.datagsm.dto.response.DataGsmStudentSearchResDto;
import team.washer.server.v2.global.thirdparty.feign.config.FeignConfig;

@FeignClient(name = "datagsm-openapi", url = "${third-party.datagsm.openapi-url:https://openapi.datagsm.kr}", configuration = FeignConfig.class)
public interface DataGsmOpenApiClient {

    @GetMapping(value = "/v1/students", produces = MediaType.APPLICATION_JSON_VALUE)
    DataGsmStudentSearchResDto searchStudents(@RequestHeader("X-API-KEY") String apiKey,
            @RequestParam("role") String role,
            @RequestParam("onlyEnrolled") boolean onlyEnrolled,
            @RequestParam("page") int page,
            @RequestParam("size") int size);
}
