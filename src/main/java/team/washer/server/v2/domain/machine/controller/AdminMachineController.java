package team.washer.server.v2.domain.machine.controller;

import org.springframework.data.domain.Pageable;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import team.washer.server.v2.domain.machine.dto.request.CreateMachineReqDto;
import team.washer.server.v2.domain.machine.dto.request.UpdateMachineReqDto;
import team.washer.server.v2.domain.machine.dto.request.UpdateMachineStatusReqDto;
import team.washer.server.v2.domain.machine.dto.response.DeleteMachineResDto;
import team.washer.server.v2.domain.machine.dto.response.MachineListResDto;
import team.washer.server.v2.domain.machine.dto.response.MachineResDto;
import team.washer.server.v2.domain.machine.dto.response.MachineStatusUpdateResDto;
import team.washer.server.v2.domain.machine.enums.MachineStatus;
import team.washer.server.v2.domain.machine.enums.MachineType;
import team.washer.server.v2.domain.machine.service.CreateMachineService;
import team.washer.server.v2.domain.machine.service.DeleteMachineService;
import team.washer.server.v2.domain.machine.service.QueryAllMachinesService;
import team.washer.server.v2.domain.machine.service.UpdateMachineService;
import team.washer.server.v2.domain.machine.service.UpdateMachineStatusService;

@RestController
@RequestMapping("/api/v2/admin/machines")
@RequiredArgsConstructor
@Validated
@Tag(name = "Admin Machine Management", description = "기기 관리 API (관리자용)")
public class AdminMachineController {

    private final QueryAllMachinesService queryAllMachinesService;
    private final UpdateMachineStatusService updateMachineStatusService;
    private final CreateMachineService createMachineService;
    private final UpdateMachineService updateMachineService;
    private final DeleteMachineService deleteMachineService;

    @GetMapping
    @Operation(summary = "전체 기기 조회", description = "필터링 옵션으로 기기 목록을 조회합니다 (페이지네이션 지원)")
    public MachineListResDto getMachines(
            @Parameter(description = "기기명 (부분 검색)") @RequestParam(required = false) String name,
            @Parameter(description = "기기 유형") @RequestParam(required = false) MachineType type,
            @Parameter(description = "층") @RequestParam(required = false) Integer floor,
            @Parameter(description = "기기 상태") @RequestParam(required = false) MachineStatus status,
            @Parameter(description = "정렬 여부 (층 → 기기종류(세탁기 우선) → 위치(왼쪽 우선) → 번호)") @RequestParam(defaultValue = "true") boolean sorted,
            Pageable pageable) {
        return queryAllMachinesService.execute(name, type, floor, status, sorted, pageable);
    }

    @PutMapping("/{id}/status")
    @Operation(summary = "기기 상태 변경", description = "기기의 상태(NORMAL/MALFUNCTION)를 변경합니다")
    public MachineStatusUpdateResDto updateMachineStatus(
            @Parameter(description = "기기 ID") @PathVariable @NotNull Long id,
            @Valid @RequestBody UpdateMachineStatusReqDto request) {
        return updateMachineStatusService.execute(id, request.status());
    }

    @PostMapping
    @Operation(summary = "기기 등록", description = "새로운 기기를 등록합니다")
    public MachineResDto createMachine(@Valid @RequestBody CreateMachineReqDto request) {
        return createMachineService.execute(request);
    }

    @PatchMapping("/{id}")
    @Operation(summary = "기기 정보 수정", description = "기기의 deviceId, 위치, 유형 정보를 수정합니다")
    public MachineResDto updateMachine(@Parameter(description = "기기 ID") @PathVariable @NotNull Long id,
            @Valid @RequestBody UpdateMachineReqDto request) {
        return updateMachineService.execute(id, request);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "기기 삭제", description = "기기를 삭제합니다. 활성 예약이 있는 경우 삭제할 수 없습니다")
    public DeleteMachineResDto deleteMachine(@Parameter(description = "기기 ID") @PathVariable @NotNull Long id) {
        return deleteMachineService.execute(id);
    }
}
