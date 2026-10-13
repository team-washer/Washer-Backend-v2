package team.washer.server.v2.domain.admin.controller;

import org.springframework.web.bind.annotation.*;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import team.washer.server.v2.domain.admin.dto.response.AdminDashboardResDto;
import team.washer.server.v2.domain.admin.service.QueryAdminDashboardService;
import team.washer.server.v2.global.common.error.dto.response.CommonErrorResponseResDto;
import team.washer.server.v2.global.config.swagger.CommonErrorResponses;

@RestController
@RequestMapping("/api/v2/admin")
@RequiredArgsConstructor
@Tag(name = "Admin Dashboard", description = "관리자 대시보드 API")
@CommonErrorResponses
public class AdminDashboardController {

    private final QueryAdminDashboardService queryAdminDashboardService;

    @GetMapping("/dashboard")
    @Operation(summary = "대시보드 통계 조회", description = "관리자 대시보드의 통계 정보를 조회합니다. 활성 예약 수, 고장 신고 현황, 현재 예약 생성이 제한된 학생 수가 포함됩니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "503", description = "예약 제한 정보를 확인할 수 없음", content = @Content(schema = @Schema(implementation = CommonErrorResponseResDto.class)))})
    public AdminDashboardResDto getDashboardStatistics() {
        return queryAdminDashboardService.execute();
    }
}
