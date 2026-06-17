package team.washer.server.v2.domain.appversion.controller;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import team.washer.server.v2.domain.appversion.dto.request.AppVersionStatusReqDto;
import team.washer.server.v2.domain.appversion.dto.response.AppVersionStatusResDto;
import team.washer.server.v2.domain.appversion.service.QueryAppVersionStatusService;

@RestController
@RequestMapping("/api/v2/app-versions")
@RequiredArgsConstructor
@Validated
@Tag(name = "App Version", description = "앱 버전 API")
public class AppVersionController {

    private final QueryAppVersionStatusService queryAppVersionStatusService;

    @GetMapping("/status")
    @Operation(summary = "앱 버전 상태 조회", description = "현재 앱 버전의 업데이트 필요 여부를 조회합니다. 앱 시작 시 인증 없이 사용할 수 있습니다.")
    public AppVersionStatusResDto queryAppVersionStatus(@Valid @ModelAttribute final AppVersionStatusReqDto reqDto) {
        return queryAppVersionStatusService.execute(reqDto);
    }
}
