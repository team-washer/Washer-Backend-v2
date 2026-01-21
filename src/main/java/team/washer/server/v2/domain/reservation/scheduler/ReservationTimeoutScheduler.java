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
import team.washer.server.v2.domain.reservation.service.ApplyReservationPenaltyService;
import team.washer.server.v2.domain.user.entity.User;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReservationTimeoutScheduler {

    private final ReservationRepository reservationRepository;
    private final ApplyReservationPenaltyService applyReservationPenaltyService;

    /**
     * 예약 타임아웃 체크 (30초마다 실행) - RESERVED: 5분 내 미확인 시 취소 + 10분 패널티 - CONFIRMED: 2분 내
     * 미시작 시 취소 (페널티 없음)
     */
    @Scheduled(fixedRate = 30000) // 30초
    @Transactional
    public void checkReservationTimeouts() {
        try {
            checkReservedTimeouts();
            checkConfirmedTimeouts();
        } catch (Exception e) {
            log.error("Error checking reservation timeouts", e);
        }
    }

    private void checkReservedTimeouts() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(5);
        LocalDateTime recentCutoff = LocalDateTime.now().minusHours(24); // 성능 최적화: 최근 24시간만

        List<Reservation> expiredReservations = reservationRepository
                .findExpiredReservations(ReservationStatus.RESERVED, threshold, recentCutoff);

        if (expiredReservations.isEmpty()) {
            return;
        }

        log.info("Found {} expired RESERVED reservations", expiredReservations.size());

        for (Reservation reservation : expiredReservations) {
            try {
                // 예약 취소
                reservation.cancel();
                reservationRepository.save(reservation);

                // 패널티 적용
                User user = reservation.getUser();
                applyReservationPenaltyService.execute(user);

                log.info("Cancelled RESERVED reservation {} due to timeout and applied penalty to user {}",
                        reservation.getId(),
                        user.getId());
            } catch (Exception e) {
                log.error("Error processing timeout for reservation {}", reservation.getId(), e);
            }
        }
    }

    private void checkConfirmedTimeouts() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(2);
        LocalDateTime recentCutoff = LocalDateTime.now().minusHours(24); // 성능 최적화: 최근 24시간만

        List<Reservation> expiredReservations = reservationRepository
                .findExpiredReservations(ReservationStatus.CONFIRMED, threshold, recentCutoff);

        if (expiredReservations.isEmpty()) {
            return;
        }

        log.info("Found {} expired CONFIRMED reservations", expiredReservations.size());

        for (Reservation reservation : expiredReservations) {
            try {
                // 예약 취소 (페널티 없음)
                reservation.cancel();
                reservationRepository.save(reservation);

                log.info("Cancelled CONFIRMED reservation {} due to timeout (no penalty)", reservation.getId());
            } catch (Exception e) {
                log.error("Error processing timeout for reservation {}", reservation.getId(), e);
            }
        }
    }
}
