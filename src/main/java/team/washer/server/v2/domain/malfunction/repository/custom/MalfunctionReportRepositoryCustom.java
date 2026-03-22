package team.washer.server.v2.domain.malfunction.repository.custom;

import java.util.List;

import team.washer.server.v2.domain.malfunction.entity.MalfunctionReport;
import team.washer.server.v2.domain.malfunction.enums.MalfunctionReportStatus;

public interface MalfunctionReportRepositoryCustom {
    List<MalfunctionReport> findWithDetails(MalfunctionReportStatus status);
}
