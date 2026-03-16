package team.washer.server.v2.domain.reservation.controller;

import java.time.LocalDateTime;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
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
import team.washer.server.v2.domain.reservation.dto.request.CreateReservationReqDto;
import team.washer.server.v2.domain.reservation.dto.response.CancellationResDto;
import team.washer.server.v2.domain.reservation.dto.response.ReservationAvailabilityResDto;
import team.washer.server.v2.domain.reservation.dto.response.ReservationHistoryPageResDto;
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
    private final CancelReservationService cancelReservationService;
    private final ConfirmReservationService confirmReservationService;
    private final QueryActiveReservationService queryActiveReservationService;
    private final QueryReservationHistoryService queryReservationHistoryService;
    private final QueryReservationService queryReservationService;
    private final QueryReservationAvailabilityService queryReservationAvailabilityService;

    @PostMapping
    @Operation(summary = "예약 생성", description = """
            세탁기/건조기 예약을 생성합니다.
            - 시간 제한 규칙이 적용됩니다 (평일/주말 예약 가능 시간대 제한).
            - 패널티 상태인 경우 예약이 불가합니다.
            - 동일 호실에 같은 유형(세탁기 또는 건조기)의 활성 예약이 이미 존재하면 예약이 불가합니다.
            - 세탁기와 건조기는 동시에 각 1개씩 예약 가능합니다.
            """)
    public ReservationResDto createReservation(
            @Parameter(description = "예약 생성 요청 DTO") @RequestBody @Valid CreateReservationReqDto requestDto) {
        return createReservationService.execute(requestDto);
    }

    @PutMapping("/{id}/confirm")
    @Operation(summary = "예약 확인", description = "예약을 확인합니다 (RESERVED → CONFIRMED). 사용자가 세탁기/건조기 앞에서 시작 버튼을 누를 때 호출됩니다.")
    public CommonApiResponse confirmReservation(@Parameter(description = "예약 ID") @PathVariable @NotNull Long id) {
        confirmReservationService.execute(id);
        return CommonApiResponse.success("예약이 확인되었습니다.");
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "예약 취소", description = "예약을 취소합니다. RESERVED 상태에서 취소 시 10분간 예약이 제한됩니다.")
    public CancellationResDto cancelReservation(@Parameter(description = "예약 ID") @PathVariable @NotNull Long id) {
        return cancelReservationService.execute(id);
    }

    @GetMapping("/active")
    @Operation(summary = "내 활성 예약 조회", description = "현재 활성 상태인 나의 예약을 조회합니다.")
    public ReservationResDto getActiveReservation() {
        return queryActiveReservationService.execute();
    }

    @GetMapping("/history")
    @Operation(summary = "예약 히스토리 조회", description = "나의 예약 히스토리를 조회합니다. 필터링과 페이지네이션을 지원합니다.")
    public ReservationHistoryPageResDto getReservationHistory(
            @Parameter(description = "예약 상태 필터") @RequestParam(required = false) ReservationStatus status,
            @Parameter(description = "시작 날짜 (yyyy-MM-dd'T'HH:mm:ss)") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @Parameter(description = "종료 날짜 (yyyy-MM-dd'T'HH:mm:ss)") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @Parameter(description = "기기 타입 필터") @RequestParam(required = false) MachineType machineType,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return queryReservationHistoryService.execute(status, startDate, endDate, machineType, pageable);
    }

    @GetMapping("/availability")
    @Operation(summary = "예약 가능 상태 조회", description = "현재 로그인된 사용자의 예약 가능 여부와 패널티 해제 시간을 조회합니다.")
    public ReservationAvailabilityResDto getReservationAvailability() {
        return queryReservationAvailabilityService.execute();
    }

    @GetMapping("/{id}")
    @Operation(summary = "예약 상세 조회", description = "예약 상세 정보를 조회합니다.")
    public ReservationResDto getReservation(@Parameter(description = "예약 ID") @PathVariable @NotNull Long id) {
        return queryReservationService.execute(id);
    }
}
