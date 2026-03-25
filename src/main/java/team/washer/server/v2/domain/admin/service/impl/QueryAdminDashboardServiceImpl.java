package team.washer.server.v2.domain.admin.service.impl;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import team.washer.server.v2.domain.admin.dto.response.AdminDashboardResDto;
import team.washer.server.v2.domain.admin.service.QueryAdminDashboardService;
import team.washer.server.v2.domain.machine.enums.MachineStatus;
import team.washer.server.v2.domain.machine.repository.MachineRepository;
import team.washer.server.v2.domain.malfunction.enums.MalfunctionReportStatus;
import team.washer.server.v2.domain.malfunction.repository.MalfunctionReportRepository;
import team.washer.server.v2.domain.reservation.repository.ReservationRepository;
import team.washer.server.v2.domain.user.repository.UserRepository;
import team.washer.server.v2.global.common.constants.PenaltyConstants;

@Service
@RequiredArgsConstructor
@Slf4j
public class QueryAdminDashboardServiceImpl implements QueryAdminDashboardService {

    private final ReservationRepository reservationRepository;
    private final MalfunctionReportRepository malfunctionReportRepository;
    private final MachineRepository machineRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public AdminDashboardResDto execute() {
        log.info("Querying admin dashboard statistics");

        var activeReservations = reservationRepository.countActiveReservations();
        var pendingReports = malfunctionReportRepository.countByStatus(MalfunctionReportStatus.PENDING);
        var processingReports = malfunctionReportRepository.countByStatus(MalfunctionReportStatus.IN_PROGRESS);
        var completedReports = malfunctionReportRepository.countByStatus(MalfunctionReportStatus.RESOLVED);
        var totalMachines = machineRepository.count();
        var malfunctionMachines = machineRepository.countByStatus(MachineStatus.MALFUNCTION);
        var penaltyThreshold = LocalDateTime.now().minusMinutes(PenaltyConstants.PENALTY_DURATION_MINUTES);
        var suspendedStudents = userRepository.countSuspendedStudents(penaltyThreshold);

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
}
