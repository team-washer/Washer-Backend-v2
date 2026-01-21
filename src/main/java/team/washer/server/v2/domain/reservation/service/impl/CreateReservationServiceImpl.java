package team.washer.server.v2.domain.reservation.service.impl;

import java.time.LocalDateTime;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import team.washer.server.v2.domain.machine.entity.Machine;
import team.washer.server.v2.domain.machine.repository.MachineRepository;
import team.washer.server.v2.domain.reservation.dto.request.CreateReservationReqDto;
import team.washer.server.v2.domain.reservation.dto.response.ReservationResDto;
import team.washer.server.v2.domain.reservation.entity.Reservation;
import team.washer.server.v2.domain.reservation.enums.ReservationStatus;
import team.washer.server.v2.domain.reservation.repository.ReservationRepository;
import team.washer.server.v2.domain.reservation.service.CreateReservationService;
import team.washer.server.v2.domain.reservation.util.PenaltyRedisUtil;
import team.washer.server.v2.domain.reservation.util.SundayReservationRedisUtil;
import team.washer.server.v2.domain.user.entity.User;
import team.washer.server.v2.domain.user.repository.UserRepository;
import team.washer.server.v2.global.common.error.exception.ExpectedException;

@Slf4j
@Service
@RequiredArgsConstructor
public class CreateReservationServiceImpl implements CreateReservationService {

    private final ReservationRepository reservationRepository;
    private final UserRepository userRepository;
    private final MachineRepository machineRepository;
    private final PenaltyRedisUtil penaltyRedisUtil;
    private final SundayReservationRedisUtil sundayReservationRedisUtil;

    @Override
    @Transactional
    public ReservationResDto execute(final Long userId, final CreateReservationReqDto reqDto) {
        final User user = userRepository.findById(userId)
                .orElseThrow(() -> new ExpectedException("사용자를 찾을 수 없습니다", HttpStatus.NOT_FOUND));

        final Machine machine = machineRepository.findById(reqDto.machineId())
                .orElseThrow(() -> new ExpectedException("기기를 찾을 수 없습니다", HttpStatus.NOT_FOUND));

        // 패널티 상태 검증
        final LocalDateTime penaltyExpiresAt = penaltyRedisUtil.getPenaltyExpiryTime(userId);
        user.validateNotPenalized(penaltyExpiresAt);

        // 시간 제한 검증 (일요일 활성화 여부 포함)
        final boolean isSundayActive = sundayReservationRedisUtil.isSundayActive();
        user.validateTimeRestriction(reqDto.startTime(), isSundayActive);

        final LocalDateTime expectedCompletionTime = reqDto.startTime().plusMinutes(90);

        // 기계 가용성 검증 (인라인)
        final boolean hasConflict = reservationRepository
                .existsConflictingReservation(machine.getId(), reqDto.startTime(), expectedCompletionTime, null);
        if (hasConflict) {
            throw new IllegalStateException(String.format("해당 시간에 기기를 사용할 수 없습니다. 기기: %s, 시간: %s ~ %s",
                    machine.getName(),
                    reqDto.startTime(),
                    expectedCompletionTime));
        }

        final Reservation reservation = Reservation.builder().user(user).machine(machine)
                .reservedAt(LocalDateTime.now()).startTime(reqDto.startTime())
                .dayOfWeek(reqDto.startTime().getDayOfWeek()).status(ReservationStatus.RESERVED).build();

        // 미래 시간 검증
        reservation.validateFutureTime();

        final Reservation saved = reservationRepository.save(reservation);
        log.info("Created reservation {} for user {} on machine {}", saved.getId(), userId, machine.getId());

        return new ReservationResDto(saved.getId(), saved.getUser().getId(), saved.getUser().getName(),
                saved.getUser().getRoomNumber(), saved.getMachine().getId(), saved.getMachine().getName(),
                saved.getReservedAt(), saved.getStartTime(), saved.getExpectedCompletionTime(),
                saved.getActualCompletionTime(), saved.getStatus(), saved.getConfirmedAt(), saved.getCancelledAt(),
                saved.getDayOfWeek(), saved.getCreatedAt(), saved.getUpdatedAt());
    }
}
