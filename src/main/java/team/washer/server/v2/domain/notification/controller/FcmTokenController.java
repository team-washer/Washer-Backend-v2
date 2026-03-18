package team.washer.server.v2.domain.notification.controller;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import team.themoment.sdk.response.CommonApiResponse;
import team.washer.server.v2.domain.notification.dto.request.FcmTokenReqDto;
import team.washer.server.v2.domain.notification.service.DeleteFcmTokenService;
import team.washer.server.v2.domain.notification.service.RegisterFcmTokenService;

@RestController
@RequestMapping("/api/v2/notifications/fcm-token")
@RequiredArgsConstructor
@Validated
@Tag(name = "FCM Token", description = "FCM 토큰 관리 API")
public class FcmTokenController {

    private final RegisterFcmTokenService registerFcmTokenService;
    private final DeleteFcmTokenService deleteFcmTokenService;

    @PostMapping
    @Operation(summary = "FCM 토큰 등록/갱신", description = "사용자의 FCM 토큰을 등록하거나 갱신합니다.")
    public CommonApiResponse registerFcmToken(
            @Parameter(description = "사용자 ID (임시: 인증 시스템 구현 후 제거 예정)", required = true) @RequestParam @NotNull Long userId,
            @RequestBody @Valid FcmTokenReqDto requestDto) {
        registerFcmTokenService.execute(userId, requestDto.token());
        return CommonApiResponse.success("FCM 토큰이 등록되었습니다.");
    }

    @DeleteMapping
    @Operation(summary = "FCM 토큰 삭제", description = "사용자의 FCM 토큰을 삭제합니다.")
    public CommonApiResponse deleteFcmToken(
            @Parameter(description = "사용자 ID (임시: 인증 시스템 구현 후 제거 예정)", required = true) @RequestParam @NotNull Long userId) {
        deleteFcmTokenService.execute(userId);
        return CommonApiResponse.success("FCM 토큰이 삭제되었습니다.");
    }
}
