package team.washer.server.v2.domain.smartthings.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import team.washer.server.v2.domain.smartthings.dto.response.SmartThingsAccessTokenResDto;
import team.washer.server.v2.domain.smartthings.service.QuerySmartThingsAccessTokenService;

/**
 * SmartThings 토큰 조회 컨트롤러
 *
 * <p>
 * 인증된 사용자가 현재 유효한 SmartThings 액세스 토큰을 조회할 수 있습니다.
 */
@RestController
@RequestMapping("/api/v2/smartthings/token")
@RequiredArgsConstructor
@Tag(name = "SmartThings Token", description = "SmartThings 토큰 API")
public class SmartThingsTokenController {

    private final QuerySmartThingsAccessTokenService querySmartThingsAccessTokenService;

    @GetMapping
    @Operation(summary = "SmartThings 액세스 토큰 조회", description = "현재 유효한 SmartThings 액세스 토큰과 만료 시각을 반환합니다. 인증된 사용자만 접근할 수 있습니다.")
    public SmartThingsAccessTokenResDto getAccessToken() {
        return querySmartThingsAccessTokenService.execute();
    }
}
