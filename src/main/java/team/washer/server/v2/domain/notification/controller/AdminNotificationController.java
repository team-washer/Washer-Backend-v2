package team.washer.server.v2.domain.notification.controller;

import org.springframework.web.bind.annotation.*;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import team.washer.server.v2.domain.notification.dto.request.TestPushNotificationReqDto;
import team.washer.server.v2.domain.notification.dto.response.TestPushNotificationResDto;
import team.washer.server.v2.domain.notification.service.SendTestPushNotificationService;
import team.washer.server.v2.global.common.error.dto.response.CommonErrorResponseResDto;
import team.washer.server.v2.global.config.swagger.CommonErrorResponses;

@RestController
@RequestMapping("/api/v2/admin/notifications")
@RequiredArgsConstructor
@Tag(name = "Admin Notification", description = "관리자 알림 API")
@SecurityRequirement(name = "bearerAuth")
@CommonErrorResponses
public class AdminNotificationController {

    private final SendTestPushNotificationService sendTestPushNotificationService;

    @PostMapping("/test")
    @Operation(summary = "테스트 푸시 알림 발송", description = "지정한 사용자에게 테스트 푸시 알림을 즉시 발송합니다. "
            + "학번을 생략하면 요청한 관리자 본인에게 발송하며, 제목과 본문을 생략하면 기본 문구를 사용합니다. " + "알림함에는 저장되지 않으므로 알림 목록 조회에는 나타나지 않습니다.")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "테스트 푸시 알림 발송 성공"),
            @ApiResponse(responseCode = "400", description = "FCM 토큰이 유효하지 않음", content = @Content(schema = @Schema(implementation = CommonErrorResponseResDto.class))),
            @ApiResponse(responseCode = "404", description = "사용자 없음 또는 FCM 토큰 미등록·만료", content = @Content(schema = @Schema(implementation = CommonErrorResponseResDto.class))),
            @ApiResponse(responseCode = "502", description = "FCM 발송 실패", content = @Content(schema = @Schema(implementation = CommonErrorResponseResDto.class)))})
    public TestPushNotificationResDto sendTestPushNotification(
            @RequestBody @Valid final TestPushNotificationReqDto reqDto) {
        return sendTestPushNotificationService.execute(reqDto);
    }
}
