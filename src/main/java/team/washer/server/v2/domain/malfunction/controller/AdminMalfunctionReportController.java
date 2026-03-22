package team.washer.server.v2.domain.malfunction.controller;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import team.washer.server.v2.domain.malfunction.dto.request.UpdateMalfunctionReportStatusReqDto;
import team.washer.server.v2.domain.malfunction.dto.response.MalfunctionReportListResDto;
import team.washer.server.v2.domain.malfunction.dto.response.MalfunctionReportResDto;
import team.washer.server.v2.domain.malfunction.enums.MalfunctionReportStatus;
import team.washer.server.v2.domain.malfunction.service.QueryMalfunctionReportListService;
import team.washer.server.v2.domain.malfunction.service.UpdateMalfunctionReportStatusService;

@RestController
@RequestMapping("/api/v2/admin/malfunction-reports")
@RequiredArgsConstructor
@Validated
@Tag(name = "Admin Malfunction Report", description = "고장 신고 관리 API (관리자용)")
public class AdminMalfunctionReportController {

    private final QueryMalfunctionReportListService queryMalfunctionReportListService;
    private final UpdateMalfunctionReportStatusService updateMalfunctionReportStatusService;

    @GetMapping
    @Operation(summary = "고장 신고 목록 조회", description = "고장 신고 목록을 조회합니다. 상태별 필터링을 지원합니다.")
    public MalfunctionReportListResDto getMalfunctionReports(
            @Parameter(description = "신고 상태 필터") @RequestParam(required = false) MalfunctionReportStatus status) {
        return queryMalfunctionReportListService.execute(status);
    }

    @PutMapping("/{id}/status")
    @Operation(summary = "고장 신고 상태 변경", description = "고장 신고의 상태를 변경합니다. IN_PROGRESS로 변경 시 기기가 고장 상태로 변경되고, RESOLVED로 변경 시 기기가 정상 상태로 복구됩니다.")
    public MalfunctionReportResDto updateMalfunctionReportStatus(
            @Parameter(description = "신고 ID") @PathVariable @NotNull Long id,
            @Parameter(description = "상태 변경 요청 DTO") @RequestBody @Valid UpdateMalfunctionReportStatusReqDto requestDto) {
        return updateMalfunctionReportStatusService.execute(id, requestDto);
    }
}
