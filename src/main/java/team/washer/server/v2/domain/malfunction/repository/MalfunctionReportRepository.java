package team.washer.server.v2.domain.malfunction.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import team.washer.server.v2.domain.machine.entity.Machine;
import team.washer.server.v2.domain.malfunction.entity.MalfunctionReport;
import team.washer.server.v2.domain.malfunction.enums.MalfunctionReportStatus;
import team.washer.server.v2.domain.malfunction.repository.custom.MalfunctionReportRepositoryCustom;
import team.washer.server.v2.domain.user.entity.User;

@Repository
public interface MalfunctionReportRepository
        extends
            JpaRepository<MalfunctionReport, Long>,
            MalfunctionReportRepositoryCustom {

    List<MalfunctionReport> findByMachine(Machine machine);

    List<MalfunctionReport> findByReporter(User reporter);

    List<MalfunctionReport> findByStatus(MalfunctionReportStatus status);

    @Query("SELECT COUNT(mr) FROM MalfunctionReport mr WHERE mr.status = :status")
    long countByStatus(@Param("status") MalfunctionReportStatus status);

    @Query("SELECT mr FROM MalfunctionReport mr WHERE mr.machine = :machine AND mr.status <> :status ORDER BY mr.reportedAt DESC")
    List<MalfunctionReport> findUnresolvedReportsByMachine(@Param("machine") Machine machine,
            @Param("status") MalfunctionReportStatus status);

    @Query("SELECT mr FROM MalfunctionReport mr WHERE mr.status = :status ORDER BY mr.reportedAt ASC")
    List<MalfunctionReport> findByStatusOrderByReportedAtAsc(@Param("status") MalfunctionReportStatus status);

    @Query("SELECT mr FROM MalfunctionReport mr WHERE mr.resolvedAt BETWEEN :startDate AND :endDate")
    List<MalfunctionReport> findResolvedBetween(@Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    /**
     * 특정 기기에 대해 다른 미해결 신고가 존재하는지 확인
     *
     * @param machine
     *            확인할 기기
     * @param status
     *            제외할 상태 (일반적으로 RESOLVED)
     * @param reportId
     *            제외할 신고 ID (현재 신고)
     * @return 다른 미해결 신고 존재 여부
     */
    boolean existsByMachineAndStatusNotAndIdNot(Machine machine, MalfunctionReportStatus status, Long reportId);
}
