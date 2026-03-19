package team.washer.server.v2.domain.notification.controller;

import java.util.Objects;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
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
@SecurityRequirement(name = "bearerAuth")
public class FcmTokenController {

    private final RegisterFcmTokenService registerFcmTokenService;
    private final DeleteFcmTokenService deleteFcmTokenService;

    @PostMapping
    @Operation(summary = "FCM 토큰 등록/갱신", description = "현재 로그인된 사용자의 FCM 토큰을 등록하거나 갱신합니다.")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "FCM 토큰 등록/갱신 성공"),
            @ApiResponse(responseCode = "400", description = "잘못된 요청", content = @Content(schema = @Schema(implementation = CommonApiResponse.class), examples = @ExampleObject(value = "{'status':400,'message':'검증 실패'}"))),
            @ApiResponse(responseCode = "401", description = "인증 필요", content = @Content(schema = @Schema(implementation = CommonApiResponse.class), examples = @ExampleObject(value = "{'status':401,'message':'인증이 필요합니다'}"))),
            @ApiResponse(responseCode = "404", description = "사용자 없음", content = @Content(schema = @Schema(implementation = CommonApiResponse.class), examples = @ExampleObject(value = "{'status':404,'message':'사용자를 찾을 수 없습니다'}")))})
    public CommonApiResponse registerFcmToken(
            @Parameter(description = "FCM 토큰 등록 요청") @RequestBody @Valid FcmTokenReqDto requestDto) {
        registerFcmTokenService.execute(requestDto.token());
        return CommonApiResponse.success("FCM 토큰이 등록되었습니다.");
    }

    @DeleteMapping
    @Operation(summary = "FCM 토큰 삭제", description = "현재 로그인된 사용자의 FCM 토큰을 삭제합니다.")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "FCM 토큰 삭제 성공"),
            @ApiResponse(responseCode = "401", description = "인증 필요", content = @Content(schema = @Schema(implementation = CommonApiResponse.class), examples = @ExampleObject(value = "{'status':401,'message':'인증이 필요합니다'}"))),
            @ApiResponse(responseCode = "404", description = "사용자 없음", content = @Content(schema = @Schema(implementation = CommonApiResponse.class), examples = @ExampleObject(value = "{'status':404,'message':'사용자를 찾을 수 없습니다'}")))})
    public CommonApiResponse deleteFcmToken() {
        deleteFcmTokenService.execute(getCurrentUserId());
        return CommonApiResponse.success("FCM 토큰이 삭제되었습니다.");
    }

    private Long getCurrentUserId() {
        return (Long) Objects.requireNonNull(SecurityContextHolder.getContext().getAuthentication()).getPrincipal();
    }
}
