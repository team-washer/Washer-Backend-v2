package team.washer.server.v2.domain.malfunction.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import team.washer.server.v2.domain.malfunction.dto.response.MalfunctionReportListResDto;
import team.washer.server.v2.domain.malfunction.dto.response.MalfunctionReportResDto;
import team.washer.server.v2.domain.malfunction.entity.MalfunctionReport;
import team.washer.server.v2.domain.malfunction.enums.MalfunctionReportStatus;
import team.washer.server.v2.domain.malfunction.repository.MalfunctionReportRepository;
import team.washer.server.v2.domain.malfunction.service.QueryMalfunctionReportListService;

@Service
@RequiredArgsConstructor
public class QueryMalfunctionReportListServiceImpl implements QueryMalfunctionReportListService {

    private final MalfunctionReportRepository malfunctionReportRepository;

    @Override
    @Transactional(readOnly = true)
    public MalfunctionReportListResDto execute(final MalfunctionReportStatus status) {
        final List<MalfunctionReport> reports;

        if (status != null) {
            reports = malfunctionReportRepository.findByStatus(status);
        } else {
            reports = malfunctionReportRepository.findAll();
        }

        final List<MalfunctionReportResDto> reportDtos = reports.stream().map(this::toResDto).toList();

        return new MalfunctionReportListResDto(reportDtos, reportDtos.size());
    }

    private MalfunctionReportResDto toResDto(final MalfunctionReport report) {
        return new MalfunctionReportResDto(report.getId(),
                report.getMachine().getId(),
                report.getMachine().getName(),
                report.getReporter().getId(),
                report.getReporter().getName(),
                report.getDescription(),
                report.getStatus(),
                report.getReportedAt(),
                report.getProcessingStartedAt(),
                report.getResolvedAt(),
                report.getCreatedAt(),
                report.getUpdatedAt());
    }
}
