package team.washer.server.v2.domain.reservation.service.impl;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import team.washer.server.v2.domain.machine.repository.MachineRepository;
import team.washer.server.v2.domain.reservation.entity.Reservation;
import team.washer.server.v2.domain.reservation.enums.ReservationStatus;
import team.washer.server.v2.domain.reservation.repository.ReservationRepository;
import team.washer.server.v2.domain.reservation.service.CancelOverdueReservationService;
import team.washer.server.v2.domain.reservation.util.PenaltyRedisUtil;
import team.washer.server.v2.domain.smartthings.service.DetectMachineRunningService;
import team.washer.server.v2.domain.smartthings.service.QueryDeviceStatusService;
import team.washer.server.v2.domain.user.entity.User;
import team.washer.server.v2.global.util.DateTimeUtil;

@Slf4j
@Service
@RequiredArgsConstructor
public class CancelOverdueReservationServiceImpl implements CancelOverdueReservationService {

    private final ReservationRepository reservationRepository;
    private final MachineRepository machineRepository;
    private final PenaltyRedisUtil penaltyRedisUtil;
    private final DetectMachineRunningService detectMachineRunningService;
    private final QueryDeviceStatusService queryDeviceStatusService;

    @Override
    @Transactional
    public void execute() {
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
                var machine = reservation.getMachine();
                var isRunning = detectMachineRunningService.execute(machine.getDeviceId());

                if (isRunning) {
                    var expectedCompletionTime = DateTimeUtil.getExpectedCompletionTime(queryDeviceStatusService,
                            machine.getDeviceId());
                    // RESERVED → CONFIRMED → RUNNING: start()은 CONFIRMED에서만 가능하므로 confirm() 선행
                    reservation.confirm();
                    reservation.start(expectedCompletionTime);
                    machine.markAsInUse();
                    reservationRepository.save(reservation);
                    machineRepository.save(machine);

                    log.info("만료된 RESERVED 예약 {}이 기기 작동 중으로 자동 시작됨", reservation.getId());
                } else {
                    reservation.cancel();
                    machine.markAsAvailable();
                    reservationRepository.save(reservation);
                    machineRepository.save(machine);
                    User user = reservation.getUser();
                    penaltyRedisUtil.applyPenalty(user);

                    log.info("만료된 RESERVED 예약 {} 취소 및 사용자 {}에게 패널티 부여", reservation.getId(), user.getId());
                }
            } catch (Exception e) {
                log.error("Error processing timeout for reservation {}", reservation.getId(), e);
            }
        }
    }
}
