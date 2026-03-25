package team.washer.server.v2.domain.malfunction.service;

import org.springframework.data.domain.Pageable;

import team.washer.server.v2.domain.malfunction.dto.response.MalfunctionReportListResDto;
import team.washer.server.v2.domain.malfunction.enums.MalfunctionReportStatus;

public interface QueryMalfunctionReportListService {
    MalfunctionReportListResDto execute(MalfunctionReportStatus status, Pageable pageable);
}
