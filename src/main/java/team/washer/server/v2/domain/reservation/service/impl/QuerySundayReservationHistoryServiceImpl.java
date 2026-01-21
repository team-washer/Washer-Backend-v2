package team.washer.server.v2.domain.reservation.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import team.washer.server.v2.domain.reservation.entity.ReservationCycleLog;
import team.washer.server.v2.domain.reservation.repository.ReservationCycleLogRepository;
import team.washer.server.v2.domain.reservation.service.QuerySundayReservationHistoryService;

/**
 * 일요일 예약 활성화 히스토리 조회 서비스 구현체
 */
@Service
@RequiredArgsConstructor
public class QuerySundayReservationHistoryServiceImpl implements QuerySundayReservationHistoryService {

    private final ReservationCycleLogRepository cycleLogRepository;

    @Override
    @Transactional(readOnly = true)
    public List<ReservationCycleLog> execute() {
        return cycleLogRepository.findAllOrderByCreatedAtDesc();
    }
}
