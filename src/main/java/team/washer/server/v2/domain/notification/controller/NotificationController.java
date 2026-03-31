package team.washer.server.v2.domain.notification.controller;

import org.springframework.web.bind.annotation.*;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import team.themoment.sdk.response.CommonApiResponse;
import team.washer.server.v2.domain.notification.dto.response.NotificationListResDto;
import team.washer.server.v2.domain.notification.service.DeleteAllNotificationsService;
import team.washer.server.v2.domain.notification.service.QueryNotificationListService;

@RestController
@RequestMapping("/api/v2/notifications")
@RequiredArgsConstructor
@Tag(name = "Notification", description = "알림 API")
@SecurityRequirement(name = "bearerAuth")
public class NotificationController {

    private final QueryNotificationListService queryNotificationListService;
    private final DeleteAllNotificationsService deleteAllNotificationsService;

    @GetMapping
    @Operation(summary = "알림 목록 조회", description = "현재 로그인된 사용자의 알림 목록을 최신순으로 조회합니다. 최대 30개까지 반환됩니다.")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "알림 목록 조회 성공"),
            @ApiResponse(responseCode = "401", description = "인증 필요", content = @Content(schema = @Schema(implementation = CommonApiResponse.class), examples = @ExampleObject(value = "{\"status\":401,\"message\":\"인증이 필요합니다\"}"))),
            @ApiResponse(responseCode = "404", description = "사용자 없음", content = @Content(schema = @Schema(implementation = CommonApiResponse.class), examples = @ExampleObject(value = "{\"status\":404,\"message\":\"사용자를 찾을 수 없습니다\"}")))})
    public NotificationListResDto getNotifications() {
        return queryNotificationListService.execute();
    }

    @DeleteMapping
    @Operation(summary = "전체 알림 삭제", description = "현재 로그인된 사용자의 모든 알림을 삭제합니다.")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "전체 알림 삭제 성공"),
            @ApiResponse(responseCode = "401", description = "인증 필요", content = @Content(schema = @Schema(implementation = CommonApiResponse.class), examples = @ExampleObject(value = "{\"status\":401,\"message\":\"인증이 필요합니다\"}"))),
            @ApiResponse(responseCode = "404", description = "사용자 없음", content = @Content(schema = @Schema(implementation = CommonApiResponse.class), examples = @ExampleObject(value = "{\"status\":404,\"message\":\"사용자를 찾을 수 없습니다\"}")))})
    public CommonApiResponse deleteAllNotifications() {
        deleteAllNotificationsService.execute();
        return CommonApiResponse.success("알림이 모두 삭제되었습니다.");
    }
}
