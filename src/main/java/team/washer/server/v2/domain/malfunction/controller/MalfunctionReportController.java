package team.washer.server.v2.domain.malfunction.controller;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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
    @Operation(summary = "고장 신고 생성", description = "기기 고장을 신고합니다.")
    public MalfunctionReportResDto createMalfunctionReport(
            @Parameter(description = "고장 신고 생성 요청 DTO") @RequestBody @Valid CreateMalfunctionReportReqDto requestDto) {
        return createMalfunctionReportService.execute(requestDto);
    }
}
