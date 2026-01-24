package team.washer.server.v2.domain.malfunction.controller;

import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import team.washer.server.v2.domain.malfunction.dto.request.CreateMalfunctionReportReqDto;
import team.washer.server.v2.domain.malfunction.dto.response.MalfunctionReportResDto;
import team.washer.server.v2.domain.malfunction.service.CreateMalfunctionReportService;

@RestController
@RequestMapping("/api/v2/malfunction-reports")
@RequiredArgsConstructor
@Validated
@Tag(name = "Malfunction Report", description = "고장 신고 API")
public class MalfunctionReportController {

    private final CreateMalfunctionReportService createMalfunctionReportService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "고장 신고 생성", description = "기기 고장을 신고합니다.")
    public MalfunctionReportResDto createMalfunctionReport(
            @Parameter(description = "사용자 ID (임시: 인증 시스템 구현 후 제거 예정)", required = true) @RequestParam @NotNull Long userId,
            @Parameter(description = "고장 신고 생성 요청 DTO") @RequestBody @Valid CreateMalfunctionReportReqDto requestDto) {
        return createMalfunctionReportService.execute(userId, requestDto);
    }
}
