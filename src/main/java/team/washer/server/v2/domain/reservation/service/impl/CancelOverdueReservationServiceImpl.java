package team.washer.server.v2.domain.reservation.service.impl;

import java.util.ArrayList;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import team.washer.server.v2.domain.reservation.service.CancelOverdueReservationService;
import team.washer.server.v2.domain.smartthings.support.DeviceStatusQuerySupport;

/**
 * 만료된 예약 취소 스케줄링 진입점.
 *
 * <p>
 * 처리 대상 조회와 개별 예약의 DB 갱신은 {@link OverdueReservationProcessor}가 독립 트랜잭션으로 수행하고,
 * 이 클래스는 트랜잭션 밖에서 SmartThings 외부 API를 호출한 뒤 그 결과를 넘겨주는 조율만 담당한다. 외부 API 호출이 DB
 * 커넥션을 점유하지 않도록 하기 위함이다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CancelOverdueReservationServiceImpl implements CancelOverdueReservationService {

    private final OverdueReservationProcessor overdueReservationProcessor;
    private final DeviceStatusQuerySupport deviceStatusQuerySupport;

    @Override
    public void execute() {
        var targets = overdueReservationProcessor.findExpiredTargets();
        if (targets.isEmpty()) {
            return;
        }

        var autoStarted = new ArrayList<Long>();
        var cancelled = new ArrayList<Long>();

        for (var target : targets) {
            try {
                var status = deviceStatusQuerySupport.queryDeviceStatus(target.deviceId());
                var result = overdueReservationProcessor.processOverdue(target.reservationId(), status);
                switch (result) {
                    case AUTO_STARTED -> autoStarted.add(target.reservationId());
                    case CANCELLED -> cancelled.add(target.reservationId());
                    case SKIPPED -> {
                    }
                }
            } catch (Exception e) {
                log.error("reservation timeout error processing RESERVED reservation={}", target.reservationId(), e);
            }
        }

        log.info("reservation timeout RESERVED processed={} auto_started={} {} cancelled={} {}",
                targets.size(),
                autoStarted.size(),
                autoStarted,
                cancelled.size(),
                cancelled);
    }
}
