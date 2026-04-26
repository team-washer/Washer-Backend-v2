package team.washer.server.v2.domain.malfunction.repository.custom;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import team.washer.server.v2.domain.malfunction.entity.MalfunctionReport;
import team.washer.server.v2.domain.malfunction.enums.MalfunctionReportStatus;

public interface MalfunctionReportRepositoryCustom {
    Page<MalfunctionReport> findWithDetails(MalfunctionReportStatus status, Pageable pageable);
}
