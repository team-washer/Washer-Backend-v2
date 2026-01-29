package team.washer.server.v2.domain.reservation.controller;

import java.time.LocalDateTime;

import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
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
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "일요일 예약 활성화", description = "일요일 예약을 활성화합니다. DORMITORY_COUNCIL 권한이 필요합니다.")
    public void activateSundayReservation(
            @Parameter(description = "관리자 ID (임시: 인증 시스템 구현 후 제거 예정)", required = true) @RequestParam @NotNull Long adminId,
            @Parameter(description = "활성화 요청 DTO") @RequestBody @Valid SundayActivationReqDto requestDto) {

        activateSundayReservationService.execute(adminId, requestDto.notes());
    }

    @PostMapping("/sunday/deactivate")
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "일요일 예약 비활성화", description = "일요일 예약을 비활성화합니다. DORMITORY_COUNCIL 권한이 필요합니다.")
    public void deactivateSundayReservation(
            @Parameter(description = "관리자 ID (임시: 인증 시스템 구현 후 제거 예정)", required = true) @RequestParam @NotNull Long adminId,
            @Parameter(description = "비활성화 요청 DTO") @RequestBody @Valid SundayActivationReqDto requestDto) {

        deactivateSundayReservationService.execute(adminId, requestDto.notes());
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
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "사용자 패널티 해제", description = "특정 사용자의 패널티를 해제합니다. ADMIN 권한이 필요합니다.")
    public void clearUserPenalty(
            @Parameter(description = "관리자 ID (임시: 인증 시스템 구현 후 제거 예정)", required = true) @RequestParam @NotNull Long adminId,
            @Parameter(description = "사용자 ID") @PathVariable @NotNull Long userId) {

        clearUserPenaltyService.execute(adminId, userId);
    }

    @GetMapping
    @Operation(summary = "전체 예약 조회", description = "필터링 옵션으로 예약 목록을 조회합니다")
    public AdminReservationListResDto getReservations(
            @Parameter(description = "사용자 이름 (부분 검색)") @RequestParam(required = false) String userName,
            @Parameter(description = "기기명 (부분 검색)") @RequestParam(required = false) String machineName,
            @Parameter(description = "예약 상태") @RequestParam(required = false) ReservationStatus status,
            @Parameter(description = "시작일") @RequestParam(required = false) LocalDateTime startDate,
            @Parameter(description = "종료일") @RequestParam(required = false) LocalDateTime endDate) {

        return queryAllReservationsService.execute(userName, machineName, status, startDate, endDate);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "예약 강제 취소", description = "관리자가 예약을 강제로 취소합니다 (패널티 없음)")
    public AdminCancellationResDto cancelReservation(@Parameter(description = "예약 ID") @PathVariable @NotNull Long id) {

        return adminCancelReservationService.execute(id);
    }
}
