package team.washer.server.v2.domain.admin.controller;

import org.springframework.web.bind.annotation.*;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import team.washer.server.v2.domain.admin.dto.response.AdminDashboardResDto;
import team.washer.server.v2.domain.admin.service.QueryAdminDashboardService;
import team.washer.server.v2.global.common.response.data.response.CommonApiResDto;

@RestController
@RequestMapping("/api/v2/admin")
@RequiredArgsConstructor
@Tag(name = "Admin Dashboard", description = "관리자 대시보드 API")
public class AdminDashboardController {

    private final QueryAdminDashboardService queryAdminDashboardService;

    @GetMapping("/dashboard")
    @Operation(summary = "대시보드 통계 조회", description = "관리자 대시보드의 통계 정보를 조회합니다. 활성 예약 수와 고장 신고 현황이 포함됩니다.")
    public CommonApiResDto<AdminDashboardResDto> getDashboardStatistics() {
        var result = queryAdminDashboardService.execute();
        return CommonApiResDto.success("대시보드 통계 조회에 성공했습니다.", result);
    }
}
