package team.washer.server.v2.domain.machine.controller;

import java.util.List;

import org.springframework.web.bind.annotation.*;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import team.washer.server.v2.domain.machine.dto.response.MachineStatusResDto;
import team.washer.server.v2.domain.machine.service.QueryAllMachinesStatusService;
import team.washer.server.v2.global.common.response.data.response.CommonApiResDto;

/**
 * 기기 관련 API 컨트롤러
 */
@RestController
@RequestMapping("/api/v2/machines")
@RequiredArgsConstructor
@Tag(name = "Machine", description = "기기 API")
public class MachineController {

    private final QueryAllMachinesStatusService queryAllMachinesStatusService;

    @GetMapping("/status")
    @Operation(summary = "전체 기기 현황 조회", description = "모든 세탁기/건조기의 실시간 상태를 조회합니다. SmartThings API와 예약 정보를 결합하여 반환합니다.")
    public CommonApiResDto<List<MachineStatusResDto>> getAllMachinesStatus() {
        var result = queryAllMachinesStatusService.execute();
        return CommonApiResDto.success("전체 기기 현황 조회에 성공했습니다.", result);
    }
}
