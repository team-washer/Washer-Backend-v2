package team.washer.server.v2.domain.reservation.controller;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import team.washer.server.v2.domain.reservation.dto.*;
import team.washer.server.v2.domain.reservation.service.ReservationPenaltyService;
import team.washer.server.v2.domain.reservation.service.SundayReservationService;
import team.washer.server.v2.domain.user.entity.User;
import team.washer.server.v2.domain.user.repository.UserRepository;

// TODO: 인증 시스템 구현 후 수정 필요
// 1. @RequestParam Long adminId 제거하고 @AuthenticationPrincipal에서 추출
// 2. 권한 검증을 @PreAuthorize로 이동 가능 (예: @PreAuthorize("hasRole('DORMITORY_COUNCIL')"))
// 3. DomainAuthorizationConfig에서 주석 처리된 인증 규칙 활성화
@RestController
@RequestMapping("/api/v2/admin/reservations")
@RequiredArgsConstructor
@Validated
@Tag(name = "Admin Reservation", description = "예약 관리 API (관리자용)")
public class AdminReservationController {

    private final SundayReservationService sundayReservationService;
    private final ReservationPenaltyService penaltyService;
    private final UserRepository userRepository;

    @PostMapping("/sunday/activate")
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "일요일 예약 활성화", description = "일요일 예약을 활성화합니다. DORMITORY_COUNCIL 권한이 필요합니다.")
    public void activateSundayReservation(
            @Parameter(description = "관리자 ID (임시: 인증 시스템 구현 후 제거 예정)", required = true) @RequestParam @NotNull Long adminId,
            @Parameter(description = "활성화 요청 DTO") @RequestBody @Valid SundayActivationRequestDto requestDto) {

        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다: " + adminId));

        if (!admin.getRole().canManageSundayReservation()) {
            throw new IllegalArgumentException("일요일 예약 관리 권한이 없습니다");
        }

        sundayReservationService.activateSundayReservation(admin, requestDto.notes());
    }

    @PostMapping("/sunday/deactivate")
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "일요일 예약 비활성화", description = "일요일 예약을 비활성화합니다. DORMITORY_COUNCIL 권한이 필요합니다.")
    public void deactivateSundayReservation(
            @Parameter(description = "관리자 ID (임시: 인증 시스템 구현 후 제거 예정)", required = true) @RequestParam @NotNull Long adminId,
            @Parameter(description = "비활성화 요청 DTO") @RequestBody @Valid SundayActivationRequestDto requestDto) {

        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다: " + adminId));

        if (!admin.getRole().canManageSundayReservation()) {
            throw new IllegalArgumentException("일요일 예약 관리 권한이 없습니다");
        }

        sundayReservationService.deactivateSundayReservation(admin, requestDto.notes());
    }

    @GetMapping("/sunday/status")
    @Operation(summary = "일요일 예약 상태 조회", description = "일요일 예약 활성화 상태와 히스토리를 조회합니다.")
    public SundayStatusDto getSundayReservationStatus() {
        boolean isActive = sundayReservationService.isSundayReservationActive();
        List<SundayActivationDto> history = sundayReservationService.getSundayReservationHistory().stream().limit(10)
                .map(SundayActivationDto::from).collect(Collectors.toList());

        return SundayStatusDto.of(isActive, history);
    }

    @GetMapping("/users/{userId}/penalty-status")
    @Operation(summary = "사용자 패널티 상태 조회", description = "특정 사용자의 패널티 상태를 조회합니다.")
    public PenaltyStatusDto getUserPenaltyStatus(
            @Parameter(description = "사용자 ID") @PathVariable @NotNull Long userId) {
        boolean isPenalized = penaltyService.isPenalized(userId);
        var penaltyExpiresAt = penaltyService.getPenaltyExpiryTime(userId);

        return PenaltyStatusDto.of(userId, isPenalized, penaltyExpiresAt);
    }

    @DeleteMapping("/users/{userId}/penalty")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "사용자 패널티 해제", description = "특정 사용자의 패널티를 해제합니다. ADMIN 권한이 필요합니다.")
    public void clearUserPenalty(
            @Parameter(description = "관리자 ID (임시: 인증 시스템 구현 후 제거 예정)", required = true) @RequestParam @NotNull Long adminId,
            @Parameter(description = "사용자 ID") @PathVariable @NotNull Long userId) {

        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다: " + adminId));

        if (!admin.getRole().isAdmin()) {
            throw new IllegalArgumentException("관리자 권한이 필요합니다");
        }

        penaltyService.clearPenalty(userId);
    }
}
