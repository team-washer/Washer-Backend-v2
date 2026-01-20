package team.washer.server.v2.domain.reservation.controller;

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
import team.washer.server.v2.domain.reservation.dto.response.PenaltyStatusResDto;
import team.washer.server.v2.domain.reservation.dto.response.SundayStatusResDto;
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
    private final QueryUserPenaltyStatusService queryUserPenaltyStatusService;
    private final ClearUserPenaltyService clearUserPenaltyService;

    @PostMapping("/sunday/activate")
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "일요일 예약 활성화", description = "일요일 예약을 활성화합니다. DORMITORY_COUNCIL 권한이 필요합니다.")
    public void activateSundayReservation(
            @Parameter(description = "관리자 ID (임시: 인증 시스템 구현 후 제거 예정)", required = true) @RequestParam @NotNull Long adminId,
            @Parameter(description = "활성화 요청 DTO") @RequestBody @Valid SundayActivationReqDto requestDto) {

        activateSundayReservationService.activateSundayReservation(adminId, requestDto.notes());
    }

    @PostMapping("/sunday/deactivate")
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "일요일 예약 비활성화", description = "일요일 예약을 비활성화합니다. DORMITORY_COUNCIL 권한이 필요합니다.")
    public void deactivateSundayReservation(
            @Parameter(description = "관리자 ID (임시: 인증 시스템 구현 후 제거 예정)", required = true) @RequestParam @NotNull Long adminId,
            @Parameter(description = "비활성화 요청 DTO") @RequestBody @Valid SundayActivationReqDto requestDto) {

        deactivateSundayReservationService.deactivateSundayReservation(adminId, requestDto.notes());
    }

    @GetMapping("/sunday/status")
    @Operation(summary = "일요일 예약 상태 조회", description = "일요일 예약 활성화 상태와 히스토리를 조회합니다.")
    public SundayStatusResDto getSundayReservationStatus() {
        return querySundayReservationStatusService.querySundayReservationStatus();
    }

    @GetMapping("/users/{userId}/penalty-status")
    @Operation(summary = "사용자 패널티 상태 조회", description = "특정 사용자의 패널티 상태를 조회합니다.")
    public PenaltyStatusResDto getUserPenaltyStatus(
            @Parameter(description = "사용자 ID") @PathVariable @NotNull Long userId) {
        return queryUserPenaltyStatusService.queryUserPenaltyStatus(userId);
    }

    @DeleteMapping("/users/{userId}/penalty")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "사용자 패널티 해제", description = "특정 사용자의 패널티를 해제합니다. ADMIN 권한이 필요합니다.")
    public void clearUserPenalty(
            @Parameter(description = "관리자 ID (임시: 인증 시스템 구현 후 제거 예정)", required = true) @RequestParam @NotNull Long adminId,
            @Parameter(description = "사용자 ID") @PathVariable @NotNull Long userId) {

        clearUserPenaltyService.clearUserPenalty(adminId, userId);
    }
}
