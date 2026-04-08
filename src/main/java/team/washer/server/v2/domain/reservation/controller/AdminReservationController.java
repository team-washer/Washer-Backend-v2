package team.washer.server.v2.domain.reservation.controller;

import java.time.LocalDateTime;

import org.springframework.data.domain.Pageable;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import team.themoment.sdk.response.CommonApiResponse;
import team.washer.server.v2.domain.machine.enums.MachineType;
import team.washer.server.v2.domain.reservation.dto.request.SundayActivationReqDto;
import team.washer.server.v2.domain.reservation.dto.response.AdminCancellationResDto;
import team.washer.server.v2.domain.reservation.dto.response.AdminReservationListResDto;
import team.washer.server.v2.domain.reservation.dto.response.PenaltyStatusResDto;
import team.washer.server.v2.domain.reservation.dto.response.SundayStatusResDto;
import team.washer.server.v2.domain.reservation.enums.ReservationStatus;
import team.washer.server.v2.domain.reservation.service.*;

@RestController
@RequestMapping("/api/v2/admin/reservations")
@RequiredArgsConstructor
@Validated
@Tag(name = "Admin Reservation", description = "예약 관리 API (관리자용)")
public class AdminReservationController {

    private final ActivateSundayReservationService activateSundayReservationService;
    private final DeactivateSundayReservationService deactivateSundayReservationService;
    private final QuerySundayReservationStatusService querySundayReservationStatusService;
    private final QueryPenaltyStatusService queryPenaltyStatusService;
    private final ClearUserPenaltyService clearUserPenaltyService;
    private final QueryAllReservationsService queryAllReservationsService;
    private final AdminCancelReservationService adminCancelReservationService;

    @PostMapping("/sunday/activate")
    @Operation(summary = "일요일 예약 활성화", description = "일요일 예약을 활성화합니다. DORMITORY_COUNCIL 권한이 필요합니다.")
    public CommonApiResponse activateSundayReservation(
            @Parameter(description = "활성화 요청 DTO") @RequestBody @Valid SundayActivationReqDto requestDto) {

        activateSundayReservationService.execute(requestDto.notes());
        return CommonApiResponse.success("일요일 예약이 활성화되었습니다.");
    }

    @PostMapping("/sunday/deactivate")
    @Operation(summary = "일요일 예약 비활성화", description = "일요일 예약을 비활성화합니다. DORMITORY_COUNCIL 권한이 필요합니다.")
    public CommonApiResponse deactivateSundayReservation(
            @Parameter(description = "비활성화 요청 DTO") @RequestBody @Valid SundayActivationReqDto requestDto) {

        deactivateSundayReservationService.execute(requestDto.notes());
        return CommonApiResponse.success("일요일 예약이 비활성화되었습니다.");
    }

    @GetMapping("/sunday/status")
    @Operation(summary = "일요일 예약 상태 조회", description = "일요일 예약 활성화 상태와 히스토리를 조회합니다.")
    public SundayStatusResDto getSundayReservationStatus() {
        return querySundayReservationStatusService.execute();
    }

    @GetMapping("/users/{userId}/penalty-status")
    @Operation(summary = "사용자 패널티 상태 조회", description = "특정 사용자의 패널티 상태를 조회합니다.")
    public PenaltyStatusResDto getUserPenaltyStatus(
            @Parameter(description = "사용자 ID") @PathVariable @NotNull Long userId) {
        return queryPenaltyStatusService.execute(userId);
    }

    @DeleteMapping("/users/{userId}/penalty")
    @Operation(summary = "사용자 패널티 해제", description = "특정 사용자의 패널티를 해제합니다. ADMIN 권한이 필요합니다.")
    public CommonApiResponse clearUserPenalty(@Parameter(description = "사용자 ID") @PathVariable @NotNull Long userId) {

        clearUserPenaltyService.execute(userId);
        return CommonApiResponse.success("사용자 패널티가 해제되었습니다.");
    }

    @GetMapping
    @Operation(summary = "전체 예약 조회", description = "필터링 옵션으로 예약 목록을 조회합니다. 상태 미지정 시 활성 예약(RESERVED, RUNNING)만 반환됩니다. 기기 유형(세탁기/건조기)으로 필터링하거나 함께 조회할 수 있습니다 (페이지네이션 지원)")
    public AdminReservationListResDto getReservations(
            @Parameter(description = "사용자 이름 (부분 검색)") @RequestParam(required = false) String userName,
            @Parameter(description = "기기명 (부분 검색)") @RequestParam(required = false) String machineName,
            @Parameter(description = "예약 상태 (미지정 시 RESERVED, RUNNING만 조회)") @RequestParam(required = false) ReservationStatus status,
            @Parameter(description = "시작일") @RequestParam(required = false) LocalDateTime startDate,
            @Parameter(description = "종료일") @RequestParam(required = false) LocalDateTime endDate,
            @Parameter(description = "기기 유형 (WASHER: 세탁기, DRYER: 건조기)") @RequestParam(required = false) MachineType machineType,
            Pageable pageable) {

        return queryAllReservationsService
                .execute(userName, machineName, status, startDate, endDate, machineType, pageable);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "예약 강제 취소", description = "관리자가 예약을 강제로 취소합니다 (패널티 없음)")
    public AdminCancellationResDto cancelReservation(@Parameter(description = "예약 ID") @PathVariable @NotNull Long id) {

        return adminCancelReservationService.execute(id);
    }
}
