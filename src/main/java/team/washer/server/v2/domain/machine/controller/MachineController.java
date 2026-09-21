package team.washer.server.v2.domain.machine.controller;

import java.time.LocalDateTime;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import team.washer.server.v2.domain.machine.dto.response.MachineDeviceStatusResDto;
import team.washer.server.v2.domain.machine.dto.response.MachineStatusListResDto;
import team.washer.server.v2.domain.machine.service.QueryAllMachinesStatusService;
import team.washer.server.v2.domain.machine.service.QueryMachineDeviceStatusService;
import team.washer.server.v2.domain.reservation.dto.response.MachineReservationHistoryPageResDto;
import team.washer.server.v2.domain.reservation.enums.ReservationStatus;
import team.washer.server.v2.domain.reservation.service.QueryMachineReservationHistoryService;
import team.washer.server.v2.global.common.error.dto.response.CommonErrorResponseResDto;
import team.washer.server.v2.global.config.swagger.CommonErrorResponses;
import team.washer.server.v2.global.security.provider.CurrentUserProvider;

@RestController
@RequestMapping("/api/v2/machines")
@RequiredArgsConstructor
@Tag(name = "Machine", description = "기기 API")
@CommonErrorResponses
public class MachineController {

    private final QueryAllMachinesStatusService queryAllMachinesStatusService;
    private final QueryMachineDeviceStatusService queryMachineDeviceStatusService;
    private final QueryMachineReservationHistoryService queryMachineReservationHistoryService;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping("/status")
    @Operation(summary = "전체 기기 현황 조회", description = "모든 세탁기/건조기의 실시간 상태를 조회합니다. SmartThings API와 예약 정보를 결합하여 반환합니다. 5층(여학생) 기숙사생은 이용할 수 없습니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "451", description = "이용 대상이 아닌 사용자", content = @Content(schema = @Schema(implementation = CommonErrorResponseResDto.class), examples = @ExampleObject(value = "{\"status\":\"UNAVAILABLE_FOR_LEGAL_REASONS\",\"code\":451,\"message\":\"해당 사용자는 세탁실을 이용할 수 없습니다.\",\"data\":{\"errorCode\":\"UNAVAILABLE_FOR_LEGAL_REASONS\",\"traceId\":\"...\"}}"))),
            @ApiResponse(responseCode = "503", description = "SmartThings 상태를 확인할 수 없음", content = @Content(schema = @Schema(implementation = CommonErrorResponseResDto.class)))})
    public MachineStatusListResDto getAllMachinesStatus(
            @Parameter(description = "정렬 여부 (층 → 기기종류(세탁기 우선) → 위치(왼쪽 우선) → 번호)") @RequestParam(defaultValue = "true") boolean sorted) {
        var result = queryAllMachinesStatusService.execute(currentUserProvider.getCurrentUserId(), sorted);
        return new MachineStatusListResDto(result, result.size());
    }

    @GetMapping("/{id}/device-status")
    @Operation(summary = "기기 실시간 상태 조회", description = "서버가 SmartThings API를 대행 호출하여 특정 기기의 작동 상태, 작업 상태, 전원 상태, 완료 예정 시간을 반환합니다. "
            + "앱은 SmartThings 액세스 토큰 없이 이 API로 상태를 조회합니다. 5층(여학생) 기숙사생은 이용할 수 없습니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "451", description = "이용 대상이 아닌 사용자", content = @Content(schema = @Schema(implementation = CommonErrorResponseResDto.class))),
            @ApiResponse(responseCode = "503", description = "SmartThings 상태를 확인할 수 없음", content = @Content(schema = @Schema(implementation = CommonErrorResponseResDto.class)))})
    public MachineDeviceStatusResDto getMachineDeviceStatus(@Parameter(description = "기기 ID") @PathVariable Long id) {
        return queryMachineDeviceStatusService.execute(currentUserProvider.getCurrentUserId(), id);
    }

    @GetMapping("/{id}/history")
    @Operation(summary = "기기별 예약 히스토리 조회", description = "특정 세탁기/건조기의 예약 히스토리를 조회합니다. 필터링과 페이지네이션을 지원합니다.")
    public MachineReservationHistoryPageResDto getMachineReservationHistory(
            @Parameter(description = "기기 ID") @PathVariable Long id,
            @Parameter(description = "예약 상태 필터") @RequestParam(required = false) ReservationStatus status,
            @Parameter(description = "시작 날짜 (yyyy-MM-dd'T'HH:mm:ss)") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @Parameter(description = "종료 날짜 (yyyy-MM-dd'T'HH:mm:ss)") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return queryMachineReservationHistoryService.execute(id, status, startDate, endDate, pageable);
    }
}
