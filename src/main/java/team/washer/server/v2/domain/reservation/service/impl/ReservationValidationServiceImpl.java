package team.washer.server.v2.domain.reservation.service.impl;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import team.washer.server.v2.domain.machine.entity.Machine;
import team.washer.server.v2.domain.reservation.repository.ReservationRepository;
import team.washer.server.v2.domain.reservation.service.ReservationPenaltyService;
import team.washer.server.v2.domain.reservation.service.ReservationValidationService;
import team.washer.server.v2.domain.reservation.service.SundayReservationService;
import team.washer.server.v2.domain.user.entity.User;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReservationValidationServiceImpl implements ReservationValidationService {

    private static final LocalTime WEEKDAY_RESTRICTION_TIME = LocalTime.of(21, 10);

    private final ReservationPenaltyService penaltyService;
    private final SundayReservationService sundayReservationService;
    private final ReservationRepository reservationRepository;

    @Override
    @Transactional(readOnly = true)
    public void validateTimeRestriction(User user, LocalDateTime startTime) {
        // DORMITORY_COUNCIL 권한은 시간 제한 우회
        if (user.canBypassTimeRestrictions()) {
            log.debug("User {} has bypass privileges, skipping time restriction", user.getId());
            return;
        }

        DayOfWeek dayOfWeek = startTime.getDayOfWeek();
        LocalTime time = startTime.toLocalTime();

        switch (dayOfWeek) {
            case MONDAY, TUESDAY, WEDNESDAY, THURSDAY :
                // 월~목: 21:10 이후만 가능
                if (time.isBefore(WEEKDAY_RESTRICTION_TIME)) {
                    throw new IllegalArgumentException(
                            String.format("월요일부터 목요일까지는 %s 이후에만 예약할 수 있습니다", WEEKDAY_RESTRICTION_TIME));
                }
                break;

            case FRIDAY, SATURDAY :
                // 금토: 제한 없음
                break;

            case SUNDAY :
                // 일요일: 활성화되어 있어야 함
                if (!sundayReservationService.isSundayReservationActive()) {
                    throw new IllegalArgumentException("일요일 예약은 현재 비활성화되어 있습니다");
                }
                break;
        }

        log.debug("Time restriction validation passed for user {} at {}", user.getId(), startTime);
    }

    @Override
    @Transactional(readOnly = true)
    public void validateUserNotPenalized(Long userId) {
        if (penaltyService.isPenalized(userId)) {
            LocalDateTime expiryTime = penaltyService.getPenaltyExpiryTime(userId);
            throw new IllegalStateException(String.format("현재 예약이 제한되어 있습니다. 제한 해제 시간: %s", expiryTime));
        }
        log.debug("Penalty validation passed for user {}", userId);
    }

    @Override
    @Transactional(readOnly = true)
    public void validateMachineAvailable(Machine machine,
            LocalDateTime startTime,
            LocalDateTime endTime,
            Long excludeReservationId) {

        boolean hasConflict = reservationRepository
                .existsConflictingReservation(machine.getId(), startTime, endTime, excludeReservationId);

        if (hasConflict) {
            throw new IllegalStateException(
                    String.format("해당 시간에 기기를 사용할 수 없습니다. 기기: %s, 시간: %s ~ %s", machine.getName(), startTime, endTime));
        }

        log.debug("Machine availability validation passed for machine {} at {} ~ {}",
                machine.getId(),
                startTime,
                endTime);
    }

    @Override
    public void validateFutureTime(LocalDateTime startTime) {
        if (startTime.isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("예약 시간은 현재 시간 이후여야 합니다");
        }
        log.debug("Future time validation passed for {}", startTime);
    }
}
