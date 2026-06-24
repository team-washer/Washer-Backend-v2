package team.washer.server.v2.domain.appversion.controller;

import java.util.List;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import team.washer.server.v2.domain.appversion.dto.request.UpsertAppVersionPolicyReqDto;
import team.washer.server.v2.domain.appversion.dto.response.AppVersionPolicyResDto;
import team.washer.server.v2.domain.appversion.enums.AppPlatform;
import team.washer.server.v2.domain.appversion.service.QueryAppVersionPoliciesService;
import team.washer.server.v2.domain.appversion.service.UpsertAppVersionPolicyService;

@RestController
@RequestMapping("/api/v2/admin/app-versions")
@RequiredArgsConstructor
@Validated
@Tag(name = "Admin App Version", description = "앱 버전 정책 관리 API")
public class AdminAppVersionController {

    private final QueryAppVersionPoliciesService queryAppVersionPoliciesService;
    private final UpsertAppVersionPolicyService upsertAppVersionPolicyService;

    @GetMapping
    @Operation(summary = "앱 버전 정책 목록 조회", description = "플랫폼별 앱 버전 정책 목록을 조회합니다.")
    public List<AppVersionPolicyResDto> queryAppVersionPolicies() {
        return queryAppVersionPoliciesService.execute();
    }

    @PutMapping("/{platform}")
    @Operation(summary = "앱 버전 정책 등록/수정", description = "플랫폼별 최신 버전, 최소 지원 버전, 스토어 URL, 업데이트 메시지를 등록하거나 수정합니다.")
    public AppVersionPolicyResDto upsertAppVersionPolicy(
            @Parameter(description = "앱 플랫폼") @PathVariable @NotNull final AppPlatform platform,
            @RequestBody @Valid final UpsertAppVersionPolicyReqDto reqDto) {
        return upsertAppVersionPolicyService.execute(platform, reqDto);
    }
}
