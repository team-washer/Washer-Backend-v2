package team.washer.server.v2.domain.reservation.service.impl;

import java.time.LocalDateTime;
import java.util.List;

import jakarta.persistence.EntityManager;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import team.washer.server.v2.domain.reservation.entity.Reservation;
import team.washer.server.v2.domain.reservation.enums.ReservationStatus;
import team.washer.server.v2.domain.reservation.repository.ReservationRepository;
import team.washer.server.v2.domain.reservation.service.CancelOverdueConfirmedReservationService;
import team.washer.server.v2.domain.reservation.util.PenaltyRedisUtil;
import team.washer.server.v2.domain.smartthings.service.DetectMachineRunningService;
import team.washer.server.v2.domain.smartthings.service.QueryDeviceStatusService;
import team.washer.server.v2.domain.user.entity.User;
import team.washer.server.v2.global.util.DateTimeUtil;

@Slf4j
@Service
@RequiredArgsConstructor
public class CancelOverdueConfirmedReservationServiceImpl implements CancelOverdueConfirmedReservationService {

    private final ReservationRepository reservationRepository;
    private final PenaltyRedisUtil penaltyRedisUtil;
    private final DetectMachineRunningService detectMachineRunningService;
    private final QueryDeviceStatusService queryDeviceStatusService;
    private final EntityManager entityManager;

    @Override
    @Transactional
    public void execute() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(3);

        List<Reservation> expiredReservations = reservationRepository
                .findExpiredConfirmedReservations(ReservationStatus.CONFIRMED, threshold);

        if (expiredReservations.isEmpty()) {
            return;
        }

        log.info("만료된 CONFIRMED 예약 {}건 발견", expiredReservations.size());

        for (Reservation reservation : expiredReservations) {
            try {
                var machine = reservation.getMachine();
                var isRunning = detectMachineRunningService.execute(machine.getDeviceId());

                if (isRunning) {
                    var expectedCompletionTime = DateTimeUtil.getExpectedCompletionTime(queryDeviceStatusService,
                            machine.getDeviceId());
                    reservation.start(expectedCompletionTime);
                    reservationRepository.save(reservation);

                    log.info("만료된 CONFIRMED 예약 {}이 기기 작동 중으로 자동 시작됨", reservation.getId());
                } else {
                    // 라이프사이클 스케줄러와의 경합 방지: 취소 전 DB 최신 상태 재조회
                    entityManager.refresh(reservation);
                    if (!reservation.isConfirmed()) {
                        log.info("예약 {}이 이미 {} 상태로 전환됨, 취소 건너뜀", reservation.getId(), reservation.getStatus());
                        continue;
                    }

                    reservation.cancel();
                    reservationRepository.save(reservation);
                    User user = reservation.getUser();
                    penaltyRedisUtil.applyPenalty(user);

                    log.info("만료된 CONFIRMED 예약 {} 취소 및 사용자 {}에게 패널티 부여", reservation.getId(), user.getId());
                }
            } catch (Exception e) {
                log.error("CONFIRMED 예약 {} 타임아웃 처리 중 오류 발생", reservation.getId(), e);
            }
        }
    }
}
