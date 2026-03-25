package team.washer.server.v2.domain.reservation.service.impl;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import team.washer.server.v2.domain.machine.repository.MachineRepository;
import team.washer.server.v2.domain.notification.service.SendAutoCancellationNotificationService;
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
    private final MachineRepository machineRepository;
    private final PenaltyRedisUtil penaltyRedisUtil;
    private final SendAutoCancellationNotificationService sendAutoCancellationNotificationService;
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

        var autoStarted = new ArrayList<Long>();
        var cancelled = new ArrayList<Long>();
        var skippedAlreadyTransitioned = new ArrayList<Long>();

        for (Reservation reservation : expiredReservations) {
            try {
                var machine = reservation.getMachine();
                var isRunning = detectMachineRunningService.execute(machine.getDeviceId());

                if (isRunning) {
                    var expectedCompletionTime = DateTimeUtil.getExpectedCompletionTime(queryDeviceStatusService,
                            machine.getDeviceId());
                    reservation.start(expectedCompletionTime);
                    machine.markAsInUse();
                    reservationRepository.save(reservation);
                    machineRepository.save(machine);
                    autoStarted.add(reservation.getId());
                } else {
                    // 라이프사이클 스케줄러와의 경합 방지: 취소 전 DB 최신 상태 재조회
                    entityManager.refresh(reservation);
                    if (!reservation.isConfirmed()) {
                        log.debug("reservation {} already transitioned to {} skipping cancellation",
                                reservation.getId(),
                                reservation.getStatus());
                        skippedAlreadyTransitioned.add(reservation.getId());
                        continue;
                    }

                    reservation.cancel();
                    machine.markAsAvailable();
                    reservationRepository.save(reservation);
                    machineRepository.save(machine);
                    User user = reservation.getUser();
                    penaltyRedisUtil.applyPenalty(user);
                    sendAutoCancellationNotificationService.execute(user, machine);
                    cancelled.add(reservation.getId());
                }
            } catch (Exception e) {
                log.error("reservation timeout error processing CONFIRMED reservation={}", reservation.getId(), e);
            }
        }

        log.info(
                "reservation timeout CONFIRMED processed={} auto_started={} {} cancelled={} {} skipped_transitioned={} {}",
                expiredReservations.size(),
                autoStarted.size(),
                autoStarted,
                cancelled.size(),
                cancelled,
                skippedAlreadyTransitioned.size(),
                skippedAlreadyTransitioned);
    }
}
