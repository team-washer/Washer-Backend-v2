package team.washer.server.v2.domain.reservation.service.impl;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import team.washer.server.v2.domain.reservation.enums.ReservationStatus;
import team.washer.server.v2.domain.reservation.service.ProcessReservationLifecycleService;
import team.washer.server.v2.domain.smartthings.support.DeviceStatusQuerySupport;

/**
 * 예약 라이프사이클 스케줄링 진입점.
 *
 * <p>
 * 처리 대상 조회와 개별 예약의 DB 갱신은 {@link ReservationLifecycleProcessor}가 독립 트랜잭션으로
 * 수행하고, 이 클래스는 트랜잭션 밖에서 SmartThings 외부 API를 호출한 뒤 그 결과를 넘겨주는 조율만 담당한다. 외부 API
 * 호출이 DB 커넥션을 점유하지 않도록 하기 위함이다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProcessReservationLifecycleServiceImpl implements ProcessReservationLifecycleService {

    private final ReservationLifecycleProcessor reservationLifecycleProcessor;
    private final DeviceStatusQuerySupport deviceStatusQuerySupport;

    @Override
    public void execute() {
        processReservedToRunning();
        processRunningToCompleted();
    }

    private void processReservedToRunning() {
        for (var target : reservationLifecycleProcessor.findTargets(ReservationStatus.RESERVED)) {
            try {
                var status = deviceStatusQuerySupport.queryDeviceStatus(target.deviceId());
                reservationLifecycleProcessor.processReservedToRunning(target.reservationId(), status);
            } catch (Exception e) {
                log.error("Failed to process RESERVED reservation: {}", target.reservationId(), e);
            }
        }
    }

    private void processRunningToCompleted() {
        for (var target : reservationLifecycleProcessor.findTargets(ReservationStatus.RUNNING)) {
            try {
                var status = deviceStatusQuerySupport.queryDeviceStatus(target.deviceId());
                reservationLifecycleProcessor.processRunningToCompleted(target.reservationId(), status);
            } catch (Exception e) {
                log.error("Failed to process RUNNING reservation: {}", target.reservationId(), e);
            }
        }
    }
}
