package team.washer.server.v2.domain.reservation.scheduler;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import team.washer.server.v2.domain.reservation.entity.Reservation;
import team.washer.server.v2.domain.reservation.enums.ReservationStatus;
import team.washer.server.v2.domain.reservation.repository.ReservationRepository;
import team.washer.server.v2.domain.reservation.util.PenaltyRedisUtil;
import team.washer.server.v2.domain.user.entity.User;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReservationTimeoutScheduler {

    private final ReservationRepository reservationRepository;
    private final PenaltyRedisUtil penaltyRedisUtil;

    @Scheduled(fixedRate = 30000)
    @Transactional
    public void checkReservationTimeouts() {
        try {
            checkReservedTimeouts();
        } catch (Exception e) {
            log.error("Error checking reservation timeouts", e);
        }
    }

    private void checkReservedTimeouts() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(5);
        LocalDateTime recentCutoff = LocalDateTime.now().minusHours(24);

        List<Reservation> expiredReservations = reservationRepository
                .findExpiredReservations(ReservationStatus.RESERVED, threshold, recentCutoff);

        if (expiredReservations.isEmpty()) {
            return;
        }

        log.info("Found {} expired RESERVED reservations", expiredReservations.size());

        for (Reservation reservation : expiredReservations) {
            try {
                // TODO: SmartThings 연동 구현 시 여기에 로직 추가
                // 1. SmartThings API를 통해 기기 상태 확인 (isMachineRunning)
                // 2. if (running) { reservation.start(); save(); return; }
                // 3. if (!running) { 아래 취소 및 패널티 로직 실행 }

                reservation.cancel();
                reservationRepository.save(reservation);
                User user = reservation.getUser();
                penaltyRedisUtil.applyPenalty(user);

                log.info("Cancelled RESERVED reservation {} due to timeout and applied penalty to user {}",
                        reservation.getId(),
                        user.getId());
            } catch (Exception e) {
                log.error("Error processing timeout for reservation {}", reservation.getId(), e);
            }
        }
    }
}
