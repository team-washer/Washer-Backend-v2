package team.washer.server.v2.domain.reservation.service.impl;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import team.washer.server.v2.domain.reservation.dto.response.SundayActivationResDto;
import team.washer.server.v2.domain.reservation.dto.response.SundayStatusResDto;
import team.washer.server.v2.domain.reservation.entity.ReservationCycleLog;
import team.washer.server.v2.domain.reservation.repository.ReservationCycleLogRepository;
import team.washer.server.v2.domain.reservation.service.QuerySundayReservationStatusService;
import team.washer.server.v2.domain.reservation.util.SundayReservationRedisUtil;

@Service
@RequiredArgsConstructor
public class QuerySundayReservationStatusServiceImpl implements QuerySundayReservationStatusService {

    private final SundayReservationRedisUtil sundayReservationRedisUtil;
    private final ReservationCycleLogRepository cycleLogRepository;

    @Override
    @Transactional(readOnly = true)
    public SundayStatusResDto execute() {
        final boolean isActive = sundayReservationRedisUtil.isSundayActive();
        final List<SundayActivationResDto> history = cycleLogRepository.findAllOrderByCreatedAtDesc().stream()
                .limit(10).map(this::mapToSundayActivationResDto).collect(Collectors.toList());

        return new SundayStatusResDto(isActive, history);
    }

    private SundayActivationResDto mapToSundayActivationResDto(final ReservationCycleLog log) {
        return new SundayActivationResDto(log.getId(),
                log.getIsActive(),
                log.getAction(),
                log.getPerformedBy().getName(),
                log.getPerformedBy().getStudentId(),
                log.getNotes(),
                log.getCreatedAt());
    }
}
