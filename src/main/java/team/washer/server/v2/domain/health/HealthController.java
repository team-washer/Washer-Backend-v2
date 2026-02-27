package team.washer.server.v2.domain.health;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import team.themoment.sdk.response.CommonApiResponse;

@RestController
@RequestMapping("/api/v2/health")
public class HealthController {
    @GetMapping
    public CommonApiResponse healthCheck() {
        return CommonApiResponse.Companion.success("OK");
    }

    @GetMapping("/error")
    public CommonApiResponse errorCheck() {
        throw new RuntimeException("INTERNAL SERVER ERROR TEST");
    }

    @GetMapping("/create")
    public CommonApiResponse createCheck() {
        return CommonApiResponse.Companion.created("CREATED");
    }
}
