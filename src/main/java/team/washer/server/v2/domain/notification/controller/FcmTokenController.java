package team.washer.server.v2.domain.notification.controller;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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
    @Operation(summary = "FCM 토큰 등록/갱신", description = "현재 로그인된 사용자의 FCM 토큰을 등록하거나 갱신합니다.")
    public CommonApiResponse registerFcmToken(@RequestBody @Valid FcmTokenReqDto requestDto) {
        registerFcmTokenService.execute(requestDto.token());
        return CommonApiResponse.success("FCM 토큰이 등록되었습니다.");
    }

    @DeleteMapping
    @Operation(summary = "FCM 토큰 삭제", description = "현재 로그인된 사용자의 FCM 토큰을 삭제합니다.")
    public CommonApiResponse deleteFcmToken() {
        deleteFcmTokenService.execute();
        return CommonApiResponse.success("FCM 토큰이 삭제되었습니다.");
    }
}
