package team.washer.server.v2.domain.reservation.service.impl;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import team.washer.server.v2.domain.machine.entity.Machine;
import team.washer.server.v2.domain.reservation.repository.ReservationRepository;
import team.washer.server.v2.domain.reservation.service.ValidateMachineAvailabilityService;

/**
 * 기기 가용성 검증 서비스 구현체
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ValidateMachineAvailabilityServiceImpl implements ValidateMachineAvailabilityService {

    private final ReservationRepository reservationRepository;

    @Override
    @Transactional(readOnly = true)
    public void execute(final Machine machine,
            final LocalDateTime startTime,
            final LocalDateTime endTime,
            final Long excludeReservationId) {

        final boolean hasConflict = reservationRepository
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
}
