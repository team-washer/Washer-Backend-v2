package team.washer.server.v2.domain.admin.service.impl;

import java.util.HashSet;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import team.washer.server.v2.domain.admin.dto.response.AdminDashboardResDto;
import team.washer.server.v2.domain.admin.repository.WashingBanRepository;
import team.washer.server.v2.domain.admin.service.QueryAdminDashboardService;
import team.washer.server.v2.domain.machine.enums.MachineStatus;
import team.washer.server.v2.domain.machine.repository.MachineRepository;
import team.washer.server.v2.domain.malfunction.enums.MalfunctionReportStatus;
import team.washer.server.v2.domain.malfunction.repository.MalfunctionReportRepository;
import team.washer.server.v2.domain.reservation.repository.ReservationRepository;
import team.washer.server.v2.domain.reservation.util.PenaltyRedisUtil;
import team.washer.server.v2.domain.user.repository.UserRepository;

@Service
@RequiredArgsConstructor
@Slf4j
public class QueryAdminDashboardServiceImpl implements QueryAdminDashboardService {

    private final ReservationRepository reservationRepository;
    private final MalfunctionReportRepository malfunctionReportRepository;
    private final MachineRepository machineRepository;
    private final UserRepository userRepository;
    private final WashingBanRepository washingBanRepository;
    private final PenaltyRedisUtil penaltyRedisUtil;

    @Override
    @Transactional(readOnly = true)
    public AdminDashboardResDto execute() {
        log.info("Querying admin dashboard statistics");

        var activeReservations = reservationRepository.countCurrentlyActive();
        var pendingReports = malfunctionReportRepository.countByStatus(MalfunctionReportStatus.PENDING);
        var processingReports = malfunctionReportRepository.countByStatus(MalfunctionReportStatus.IN_PROGRESS);
        var completedReports = malfunctionReportRepository.countByStatus(MalfunctionReportStatus.RESOLVED);
        var totalMachines = machineRepository.count();
        var malfunctionMachines = machineRepository.countByStatus(MachineStatus.MALFUNCTION);
        var suspendedStudents = countSuspendedStudents();

        var result = new AdminDashboardResDto(activeReservations,
                pendingReports,
                processingReports,
                completedReports,
                totalMachines,
                malfunctionMachines,
                suspendedStudents);

        log.info("Successfully queried admin dashboard statistics");

        return result;
    }

    /**
     * 현재 예약 생성이 제한되는 고유 사용자 수를 셉니다.
     * <p>
     * 기기 유형별 쿨다운(Redis) 대상 사용자와, 호실 차단(Redis) 또는 세탁 금지(DB)가 적용된 호실의 소속 사용자를 사용자 기준
     * 합집합으로 셉니다. Redis 조회에 실패하면 부정확한 값을 반환하지 않고 예외를 던집니다.
     * </p>
     */
    private long countSuspendedStudents() {
        final var cooldownUserIds = penaltyRedisUtil.findCooldownUserIdsOrThrow();
        final var restrictedRoomNumbers = new HashSet<>(penaltyRedisUtil.findBlockedRoomNumbersOrThrow());
        restrictedRoomNumbers.addAll(washingBanRepository.findAllRoomNumbers());

        if (cooldownUserIds.isEmpty() && restrictedRoomNumbers.isEmpty()) {
            return 0L;
        }
        return userRepository.countByIdInOrRoomNumberIn(cooldownUserIds, restrictedRoomNumbers);
    }
}
