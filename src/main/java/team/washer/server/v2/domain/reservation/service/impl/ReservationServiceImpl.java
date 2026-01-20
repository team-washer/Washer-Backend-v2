package team.washer.server.v2.domain.reservation.service.impl;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import team.washer.server.v2.domain.machine.entity.Machine;
import team.washer.server.v2.domain.machine.enums.MachineType;
import team.washer.server.v2.domain.machine.repository.MachineRepository;
import team.washer.server.v2.domain.reservation.dto.*;
import team.washer.server.v2.domain.reservation.entity.Reservation;
import team.washer.server.v2.domain.reservation.enums.ReservationStatus;
import team.washer.server.v2.domain.reservation.repository.ReservationRepository;
import team.washer.server.v2.domain.reservation.service.*;
import team.washer.server.v2.domain.user.entity.User;
import team.washer.server.v2.domain.user.repository.UserRepository;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReservationServiceImpl implements ReservationService {

    private final ReservationRepository reservationRepository;
    private final UserRepository userRepository;
    private final MachineRepository machineRepository;
    private final ReservationValidationService validationService;
    private final ReservationPenaltyService penaltyService;

    @Override
    @Transactional
    public ReservationResponseDto createReservation(Long userId, CreateReservationRequestDto requestDto) {
        // 사용자 조회
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다: " + userId));

        // 기기 조회
        Machine machine = machineRepository.findById(requestDto.machineId())
                .orElseThrow(() -> new IllegalArgumentException("기기를 찾을 수 없습니다: " + requestDto.machineId()));

        // 검증
        validationService.validateFutureTime(requestDto.startTime());
        validationService.validateTimeRestriction(user, requestDto.startTime());
        validationService.validateUserNotPenalized(userId);

        // 예상 완료 시간 계산 (기본 90분)
        LocalDateTime expectedCompletionTime = requestDto.startTime().plusMinutes(90);

        validationService.validateMachineAvailable(machine, requestDto.startTime(), expectedCompletionTime, null);

        // 예약 생성
        Reservation reservation = Reservation.builder()
                .user(user)
                .machine(machine)
                .reservedAt(LocalDateTime.now())
                .startTime(requestDto.startTime())
                .dayOfWeek(requestDto.startTime().getDayOfWeek())
                .status(ReservationStatus.RESERVED)
                .build();

        Reservation saved = reservationRepository.save(reservation);
        log.info("Created reservation {} for user {} on machine {}", saved.getId(), userId, machine.getId());

        return ReservationResponseDto.from(saved);
    }

    @Override
    @Transactional
    public ReservationResponseDto confirmReservation(Long userId, Long reservationId) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new IllegalArgumentException("예약을 찾을 수 없습니다: " + reservationId));

        // 권한 검증
        if (!reservation.getUser().getId().equals(userId)) {
            throw new IllegalArgumentException("예약을 확인할 권한이 없습니다");
        }

        // 상태 검증
        if (!reservation.isReserved()) {
            throw new IllegalStateException("예약 상태에서만 확인할 수 있습니다");
        }

        // 만료 검증
        if (reservation.isExpired()) {
            throw new IllegalStateException("예약이 만료되었습니다");
        }

        reservation.confirm();
        Reservation saved = reservationRepository.save(reservation);
        log.info("Confirmed reservation {} by user {}", reservationId, userId);

        return ReservationResponseDto.from(saved);
    }

    @Override
    @Transactional
    public ReservationResponseDto startReservation(Long userId, Long reservationId,
            StartReservationRequestDto requestDto) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new IllegalArgumentException("예약을 찾을 수 없습니다: " + reservationId));

        // 권한 검증
        if (!reservation.getUser().getId().equals(userId)) {
            throw new IllegalArgumentException("예약을 시작할 권한이 없습니다");
        }

        // 상태 검증
        if (!reservation.isConfirmed()) {
            throw new IllegalStateException("확인된 예약만 시작할 수 있습니다");
        }

        // 만료 검증
        if (reservation.isExpired()) {
            throw new IllegalStateException("예약이 만료되었습니다");
        }

        reservation.start(requestDto.expectedCompletionTime());
        Reservation saved = reservationRepository.save(reservation);
        log.info("Started reservation {} by user {}", reservationId, userId);

        return ReservationResponseDto.from(saved);
    }

    @Override
    @Transactional
    public CancellationResponseDto cancelReservation(Long userId, Long reservationId) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new IllegalArgumentException("예약을 찾을 수 없습니다: " + reservationId));

        // 권한 검증
        if (!reservation.getUser().getId().equals(userId)) {
            throw new IllegalArgumentException("예약을 취소할 권한이 없습니다");
        }

        // 상태 검증
        if (!reservation.isActive()) {
            throw new IllegalStateException("활성 예약만 취소할 수 있습니다");
        }

        boolean applyPenalty = false;
        LocalDateTime penaltyExpiresAt = null;

        // 패널티 적용 조건: RESERVED 상태에서 취소 (5분 내 미확인은 스케줄러에서 처리)
        if (reservation.isReserved() && !reservation.isExpired()) {
            User user = reservation.getUser();
            penaltyService.applyPenalty(user);
            penaltyExpiresAt = penaltyService.getPenaltyExpiryTime(userId);
            applyPenalty = true;
            log.info("Applied penalty to user {} for cancelling reservation {}", userId, reservationId);
        }

        reservation.cancel();
        reservationRepository.save(reservation);
        log.info("Cancelled reservation {} by user {}", reservationId, userId);

        return CancellationResponseDto.of(applyPenalty, penaltyExpiresAt);
    }

    @Override
    @Transactional(readOnly = true)
    public ReservationResponseDto getActiveReservation(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다: " + userId));

        List<Reservation> activeReservations = reservationRepository.findByUserAndStatusIn(user,
                List.of(ReservationStatus.RESERVED, ReservationStatus.CONFIRMED, ReservationStatus.RUNNING));

        if (activeReservations.isEmpty()) {
            return null;
        }

        // 최신 활성 예약 반환
        Reservation latest = activeReservations.stream()
                .max((r1, r2) -> r1.getCreatedAt().compareTo(r2.getCreatedAt()))
                .orElse(null);

        return latest != null ? ReservationResponseDto.from(latest) : null;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ReservationHistoryDto> getReservationHistory(Long userId, ReservationStatus status,
            LocalDateTime startDate, LocalDateTime endDate, MachineType machineType, Pageable pageable) {

        Page<Reservation> reservations = reservationRepository.findReservationHistory(userId, status, startDate,
                endDate, machineType, pageable);

        return reservations.map(ReservationHistoryDto::from);
    }

    @Override
    @Transactional(readOnly = true)
    public ReservationResponseDto getReservation(Long reservationId) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new IllegalArgumentException("예약을 찾을 수 없습니다: " + reservationId));

        return ReservationResponseDto.from(reservation);
    }
}
