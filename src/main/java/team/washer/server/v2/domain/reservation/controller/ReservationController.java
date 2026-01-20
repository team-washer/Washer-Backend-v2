package team.washer.server.v2.domain.reservation.controller;

import java.time.LocalDateTime;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import team.washer.server.v2.domain.machine.enums.MachineType;
import team.washer.server.v2.domain.reservation.dto.request.CreateReservationReqDto;
import team.washer.server.v2.domain.reservation.dto.request.StartReservationReqDto;
import team.washer.server.v2.domain.reservation.dto.response.CancellationResDto;
import team.washer.server.v2.domain.reservation.dto.response.ReservationHistoryResDto;
import team.washer.server.v2.domain.reservation.dto.response.ReservationResDto;
import team.washer.server.v2.domain.reservation.enums.ReservationStatus;
import team.washer.server.v2.domain.reservation.service.*;

@RestController
@RequestMapping("/api/v2/reservations")
@RequiredArgsConstructor
@Validated
@Tag(name = "Reservation", description = "예약 API")
public class ReservationController {

    private final CreateReservationService createReservationService;
    private final ConfirmReservationService confirmReservationService;
    private final StartReservationService startReservationService;
    private final CancelReservationService cancelReservationService;
    private final GetActiveReservationService getActiveReservationService;
    private final GetReservationHistoryService getReservationHistoryService;
    private final GetReservationService getReservationService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "예약 생성", description = "세탁기/건조기 예약을 생성합니다. 시간 제한과 패널티 규칙이 적용됩니다.")
    public ReservationResDto createReservation(
            @Parameter(description = "사용자 ID (임시: 인증 시스템 구현 후 제거 예정)", required = true) @RequestParam @NotNull Long userId,
            @Parameter(description = "예약 생성 요청 DTO") @RequestBody @Valid CreateReservationReqDto requestDto) {
        return createReservationService.createReservation(userId, requestDto);
    }

    @PutMapping("/{id}/confirm")
    @Operation(summary = "예약 확인", description = "예약을 확인합니다. 5분 내에 확인하지 않으면 자동 취소되며 패널티가 적용됩니다.")
    public ReservationResDto confirmReservation(
            @Parameter(description = "사용자 ID (임시: 인증 시스템 구현 후 제거 예정)", required = true) @RequestParam @NotNull Long userId,
            @Parameter(description = "예약 ID") @PathVariable @NotNull Long id) {
        return confirmReservationService.confirmReservation(userId, id);
    }

    @PutMapping("/{id}/start")
    @Operation(summary = "기기 시작", description = "확인된 예약의 기기를 시작합니다. 2분 내에 시작하지 않으면 자동 취소됩니다.")
    public ReservationResDto startReservation(
            @Parameter(description = "사용자 ID (임시: 인증 시스템 구현 후 제거 예정)", required = true) @RequestParam @NotNull Long userId,
            @Parameter(description = "예약 ID") @PathVariable @NotNull Long id,
            @Parameter(description = "시작 요청 DTO") @RequestBody @Valid StartReservationReqDto requestDto) {
        return startReservationService.startReservation(userId, id, requestDto);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "예약 취소", description = "예약을 취소합니다. RESERVED 상태에서 취소 시 10분간 예약이 제한됩니다.")
    public CancellationResDto cancelReservation(
            @Parameter(description = "사용자 ID (임시: 인증 시스템 구현 후 제거 예정)", required = true) @RequestParam @NotNull Long userId,
            @Parameter(description = "예약 ID") @PathVariable @NotNull Long id) {
        return cancelReservationService.cancelReservation(userId, id);
    }

    @GetMapping("/active")
    @Operation(summary = "내 활성 예약 조회", description = "현재 활성 상태인 나의 예약을 조회합니다.")
    public ReservationResDto getActiveReservation(
            @Parameter(description = "사용자 ID (임시: 인증 시스템 구현 후 제거 예정)", required = true) @RequestParam @NotNull Long userId) {
        return getActiveReservationService.getActiveReservation(userId);
    }

    @GetMapping("/history")
    @Operation(summary = "예약 히스토리 조회", description = "나의 예약 히스토리를 조회합니다. 필터링과 페이지네이션을 지원합니다.")
    public Page<ReservationHistoryResDto> getReservationHistory(
            @Parameter(description = "사용자 ID (임시: 인증 시스템 구현 후 제거 예정)", required = true) @RequestParam @NotNull Long userId,
            @Parameter(description = "예약 상태 필터") @RequestParam(required = false) ReservationStatus status,
            @Parameter(description = "시작 날짜 (yyyy-MM-dd'T'HH:mm:ss)") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @Parameter(description = "종료 날짜 (yyyy-MM-dd'T'HH:mm:ss)") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @Parameter(description = "기기 타입 필터") @RequestParam(required = false) MachineType machineType,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return getReservationHistoryService
                .getReservationHistory(userId, status, startDate, endDate, machineType, pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "예약 상세 조회", description = "예약 상세 정보를 조회합니다.")
    public ReservationResDto getReservation(@Parameter(description = "예약 ID") @PathVariable @NotNull Long id) {
        return getReservationService.getReservation(id);
    }
}
