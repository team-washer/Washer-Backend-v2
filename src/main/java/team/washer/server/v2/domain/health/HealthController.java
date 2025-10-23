package team.washer.server.v2.domain.health;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import team.washer.server.v2.global.common.response.data.response.CommonApiResDto;

@RestController
@RequestMapping("/api/v2/health")
public class HealthController {
    @GetMapping
    public CommonApiResDto healthCheck() {
        return CommonApiResDto.success("OK");
    }
}
